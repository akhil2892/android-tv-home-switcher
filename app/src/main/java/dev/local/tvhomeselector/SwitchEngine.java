package dev.local.tvhomeselector;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Whitelisted switching operations, gated by the recorded firmware/user and observed state. */
final class SwitchEngine {
    interface Shell { AdbWire.ShellResult run(String command) throws Exception; }
    static final class Profile {
        final String fingerprint,user,stock,settings,inputs; final Set<String> baseline;
        Profile(String fingerprint,String user,String stock,String settings,String inputs,Set<String> baseline){
            this.fingerprint=fingerprint;this.user=user;this.stock=canonical(stock);
            this.settings=canonical(settings);this.inputs=canonical(inputs);this.baseline=new HashSet<>(baseline);
            if(!user.matches("[0-9]+"))throw new IllegalArgumentException("Invalid user");
        }
        String stockPackage(){return stock.substring(0,stock.indexOf('/'));}
    }
    final Profile profile; final Shell shell; final Consumer<String> log;
    boolean mutationBegan;
    SwitchEngine(Profile profile,Shell shell,Consumer<String> log){this.profile=profile;this.shell=shell;this.log=log;}
    static String canonical(String component){
        if(component==null || !component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"))
            throw new IllegalArgumentException("Invalid launcher component");
        int slash=component.indexOf('/');
        return component.charAt(slash+1)=='.' ? component.substring(0,slash+1)+component.substring(0,slash)+component.substring(slash+1) : component;
    }
    static String command(String... args){
        StringBuilder value=new StringBuilder();
        for(String arg:args){if(value.length()>0)value.append(' ');value.append('\'').append(arg.replace("'","'\\''")).append('\'');}
        return value.toString();
    }
    private String run(String... args) throws Exception {
        String cmd=command(args); AdbWire.ShellResult result=shell.run(cmd);
        log.accept("$ "+cmd+"\nexit="+result.exit+"\n"+result.stdout+(result.stderr.isEmpty()?"":"\nstderr: "+result.stderr));
        if(result.exit!=0 || (result.stdout+result.stderr).matches("(?s).*(?:SecurityException|Permission Denial|(?m:^Error:)|(?m:^Failure)).*"))
            throw new IOException("Command failed (exit "+result.exit+"): "+String.join(" ",args));
        return result.stdout.trim();
    }
    void verifyIdentity() throws Exception {
        if(!"2000".equals(run("id","-u")))throw new IOException("Expected authorized ADB shell UID 2000");
        if(!profile.fingerprint.equals(run("getprop","ro.build.fingerprint")))throw new IOException("Firmware differs from the recorded TV. No switch performed.");
        if(!profile.user.equals(run("am","get-current-user")))throw new IOException("Android user differs from the recorded user. No switch performed.");
    }
    private String[] homeQuery(String verb){return new String[]{"cmd","package",verb,"--components","--user",profile.user,"-a","android.intent.action.MAIN","-c","android.intent.category.HOME","-c","android.intent.category.DEFAULT"};}
    static Set<String> components(String output){
        Set<String> result=new HashSet<>();
        for(String line:output.split("\\r?\\n")){try{result.add(canonical(line.trim()));}catch(IllegalArgumentException ignored){}}
        return result;
    }
    String resolution() throws Exception {
        Set<String> values=components(run("cmd","package","resolve-activity","--brief","--user",profile.user,"-a","android.intent.action.MAIN","-c","android.intent.category.HOME","-c","android.intent.category.DEFAULT"));
        if(values.size()!=1)throw new IOException("HOME resolution is unavailable or ambiguous");
        return values.iterator().next();
    }
    private Set<String> disabled() throws Exception {
        Set<String> result=new HashSet<>();
        for(String line:run("pm","list","packages","-d","--user",profile.user).split("\\r?\\n")){
            if(line.trim().startsWith("package:"))result.add(line.trim().substring(8));
        }
        return result;
    }
    private void verifyDisabled(boolean stockDisabled) throws Exception {
        Set<String> expected=new HashSet<>(profile.baseline);if(stockDisabled)expected.add(profile.stockPackage());
        if(!expected.equals(disabled()))throw new IOException("Disabled packages differ from the reviewed baseline. Recheck on the laptop.");
    }
    private void controls() throws Exception {
        if(!components(run("cmd","package","query-activities","--components","--user",profile.user,"-a","android.settings.SETTINGS")).contains(profile.settings))
            throw new IOException("Independent Sony Settings is unavailable");
        if(!components(run("cmd","package","query-activities","--components","--user",profile.user,"-a","com.android.tv.action.VIEW_INPUTS")).contains(profile.inputs))
            throw new IOException("Independent Sony Inputs is unavailable");
    }
    String probe() throws Exception {
        verifyIdentity(); controls();
        Set<String> expected=new HashSet<>(profile.baseline),actual=disabled();
        expected.add(profile.stockPackage());
        if(!actual.equals(profile.baseline)&&!actual.equals(expected))throw new IOException("Package state differs from the reviewed baseline");
        return resolution();
    }
    private void launch(String target) throws Exception {
        String output=run("am","start","-W","--user",profile.user,"-a","android.intent.action.MAIN","-c","android.intent.category.HOME","-n",target);
        if(!output.contains("Status: ok"))throw new IOException("Explicit Home launch was not confirmed");
    }
    void switchTo(String component) throws Exception {
        String target=canonical(component);
        String current=probe(); // all gates precede package changes
        if(target.equals(profile.stock)){
            mutationBegan=true;
            if(!run("pm","default-state","--user",profile.user,profile.stockPackage()).contains("new state: default"))throw new IOException("Stock restoration was not confirmed");
        } else {
            if(!components(run(homeQuery("query-activities"))).contains(target))throw new IOException("Requested Home is no longer an enabled HOME candidate");
            if(target.toLowerCase(Locale.ROOT).contains("fallback")||target.toLowerCase(Locale.ROOT).contains("resolver"))throw new IOException("System fallback is not selectable");
            // Prove that the replacement opens before changing stock state.
            launch(target);
            verifyIdentity();
            mutationBegan=true;
            if(!run("pm","disable-user","--user",profile.user,profile.stockPackage()).contains("new state: disabled-user"))throw new IOException("Stock disable was not confirmed");
        }
        if(!run("cmd","package","set-home-activity","--user",profile.user,target).equals("Success"))throw new IOException("Home preference was not confirmed");
        if(!target.equals(resolution()))throw new IOException("Effective HOME did not change to the selected launcher");
        controls(); verifyDisabled(!target.equals(profile.stock)); launch(target);
        log.accept("Verified HOME: "+target+" (previous: "+current+"). Physical remote check is separate.");
    }
    void restoreStock() throws Exception {
        verifyIdentity();
        if(!run("pm","default-state","--user",profile.user,profile.stockPackage()).contains("new state: default"))throw new IOException("Stock restoration was not confirmed");
        if(!run("cmd","package","set-home-activity","--user",profile.user,profile.stock).equals("Success"))throw new IOException("Stock preference was not confirmed");
        if(!profile.stock.equals(resolution()))throw new IOException("Stock HOME resolution was not restored");
        launch(profile.stock);
    }
}
