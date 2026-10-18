package dev.local.tvhomeselector;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Remote-operated frontend. Shell operations require a separate TV-approved local ADB key. */
public final class SwitchActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Button> buttons=new ArrayList<>();
    private TextView status;
    private SwitchEngine.Profile profile;
    private String custom;
    private boolean busy,verified;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        try {
            byte[] bytes;
            try(InputStream input=getAssets().open("verified-tv.json")){bytes=readAsset(input);}
            JSONObject config=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            Set<String> baseline=new HashSet<>();JSONArray disabled=config.getJSONArray("originalDisabledPackages");
            for(int i=0;i<disabled.length();i++)baseline.add(disabled.getString(i));
            profile=new SwitchEngine.Profile(config.getString("fingerprint"),config.getString("user"),
                    config.getString("stockComponent"),config.getString("settingsComponent"),config.getString("inputsComponent"),baseline);
            custom=SwitchEngine.canonical(config.getString("testedCustomComponent"));
            layout();
        }catch(Exception error){
            TextView text=new TextView(this);text.setText("Recorded TV configuration could not be loaded: "+error.getMessage());setContentView(text);
        }
    }
    private static byte[] readAsset(InputStream input)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[2048];int count;
        while((count=input.read(buffer))!=-1){if(out.size()+count>32768)throw new IOException("Configuration too large");out.write(buffer,0,count);}return out.toByteArray();
    }
    @Override public void onResume(){super.onResume();if(status!=null&&!busy)showStatus();}
    @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
    private int dp(int size){return Math.round(size*getResources().getDisplayMetrics().density);}
    private void layout(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF101827);scroll.setFillViewport(true);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(40),dp(24),dp(40),dp(28));panel.setBackgroundColor(0xFF101827);scroll.addView(panel);setContentView(scroll);
        text(panel,"Home Switcher",28,Color.WHITE);
        text(panel,"Choose which Home opens when you press the remote’s Home button.",18,0xFFCCD7E7);
        status=text(panel,"",16,0xFF9DE6FF);
        button(panel,"Connect and check",()->operate(null));
        button(panel,"Use stock Home",()->confirm(profile.stock,"stock Home"));
        button(panel,"Use tv-vibehack",()->confirm(custom,"tv-vibehack"));
        button(panel,"Other installed Homes…",this::otherHomes);
        LinearLayout tools=new LinearLayout(this);panel.addView(tools);
        button(tools,"Settings",()->open(new Intent(Settings.ACTION_SETTINGS)));
        button(tools,"Inputs",()->open(new Intent("com.android.tv.action.VIEW_INPUTS").setComponent(ComponentName.unflattenFromString(profile.inputs))));
        button(tools,"Launcher tools",()->open(new Intent(this,MainActivity.class)));
        text(panel,"First use: choose Connect and check, then approve this app’s “Allow USB debugging?” prompt. Select Always allow. Switching requires USB debugging to remain enabled.",15,0xFFCCD7E7);
        text(panel,"Custom mode turns off only the recorded stock launcher package. Stock mode restores it. Sony Settings and Inputs are separate. Launchers keep their app data.",15,0xFFCCD7E7);
        showStatus();buttons.get(0).requestFocus();
    }
    private void showStatus(){
        String resolved="unavailable";
        try{ResolveInfo info=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT),PackageManager.MATCH_DEFAULT_ONLY);
            if(info!=null&&info.activityInfo!=null){String comp=new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString();resolved=comp.equals(profile.stock)?"stock Home":comp.equals(custom)?"tv-vibehack":comp;}}
        catch(RuntimeException ignored){}
        String previous=getPreferences(MODE_PRIVATE).getString("lastResult","Choose Connect and check to authorize and verify this TV.");
        status.setText("Current Home: "+resolved+"\n"+previous);
    }
    private void confirm(String target,String label){
        if(!verified){new AlertDialog.Builder(this).setTitle("Connect first").setMessage("Choose Connect and check once to approve this app and verify your TV. No launcher will change during that check.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(this).setTitle("Use "+label+"?")
                .setMessage(target.equals(profile.stock)?"Restore stock Home and make the remote’s Home button open it?":"Make the remote’s Home button open "+label+"? The stock launcher package will be turned off. You can restore it here with Use stock Home.")
                .setNegativeButton("Cancel",null).setPositiveButton("Switch",(dialog,which)->operate(target)).show();
    }
    private void otherHomes(){
        List<String> components=new ArrayList<>(),labels=new ArrayList<>();
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addCategory(Intent.CATEGORY_DEFAULT);
        for(ResolveInfo info:getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)){
            ActivityInfo activity=info.activityInfo;if(activity==null||!activity.exported||!activity.enabled||!activity.applicationInfo.enabled)continue;
            if(activity.permission!=null&&checkSelfPermission(activity.permission)!=PackageManager.PERMISSION_GRANTED)continue;
            String component=new ComponentName(activity.packageName,activity.name).flattenToString();
            if(component.equals(profile.stock)||component.equals(custom)||component.toLowerCase(Locale.ROOT).contains("fallback")||component.toLowerCase(Locale.ROOT).contains("resolver"))continue;
            components.add(component);labels.add(info.loadLabel(getPackageManager())+"\n"+component);
        }
        if(components.isEmpty()){new AlertDialog.Builder(this).setTitle("Other Homes").setMessage("No other enabled, accessible Home launchers are installed. Install a compatible launcher, test it with Launcher tools → Launch once, then return here. New launchers need their own playback and remote checks.").setPositiveButton("OK",null).show();return;}
        new AlertDialog.Builder(this).setTitle("Test a new Home with Launcher tools first")
                .setItems(labels.toArray(new String[0]),(dialog,which)->confirm(components.get(which),labels.get(which).split("\n")[0])).setNegativeButton("Cancel",null).show();
    }
    private void operate(String target){
        if(busy)return;busy=true;for(Button button:buttons)button.setEnabled(false);
        status.setText("Connecting to this TV’s local debugging service…");
        worker.execute(()->{
            StringBuilder capture=new StringBuilder();SwitchEngine engine=null;boolean success=false;String outcome;
            try{
                AdbIdentity identity=AdbIdentity.load(getFilesDir());
                try(LocalAdb adb=new LocalAdb(identity,message->progress(message))){
                    engine=new SwitchEngine(profile,adb,message->{capture.append(message).append('\n');});
                    if(target==null){String resolved=engine.probe();outcome="Connected and verified. Choose a Home below. Resolved: "+resolved;}
                    else{engine.switchTo(target);outcome="Switch verified. Press physical Home to check the remote.";}
                }
                success=true;
            }catch(Exception error){
                outcome="Could not complete: "+error.getClass().getSimpleName()+": "+error.getMessage();capture.append(outcome).append('\n');
                if(engine!=null&&engine.mutationBegan){
                    progress("Switch verification failed. Restoring stock Home…");
                    try(LocalAdb recovery=new LocalAdb(AdbIdentity.load(getFilesDir()),this::progress)){
                        new SwitchEngine(profile,recovery,message->capture.append(message).append('\n')).restoreStock();
                        outcome+=" Stock Home was restored and verified.";
                    }catch(Exception restoreError){outcome+=" Automatic recovery could not be verified: "+restoreError.getMessage()+". Run Restore-HomePackage.ps1 -RestorePreference on the laptop.";capture.append(outcome).append('\n');}
                }
            }
            capture.append(outcome).append('\n');
            try(FileOutputStream file=openFileOutput("last-switch.log",MODE_PRIVATE)){file.write(capture.toString().getBytes(StandardCharsets.UTF_8));}
            catch(IOException error){outcome+=" (Could not save the local diagnostic log.)";}
            getPreferences(MODE_PRIVATE).edit().putString("lastResult",outcome).apply();
            final String result=outcome;final boolean passed=success;
            runOnUiThread(()->{busy=false;verified=passed;for(Button button:buttons)button.setEnabled(true);if(!isFinishing()&&!isDestroyed()){showStatus();if(!passed)new AlertDialog.Builder(this).setTitle("Switch status").setMessage(result).setPositiveButton("OK",null).show();}});
        });
    }
    private void progress(String message){runOnUiThread(()->{if(!isDestroyed())status.setText(message);});}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException error){status.setText("Could not open: "+error.getMessage());}}
    private TextView text(LinearLayout panel,String value,int size,int color){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setPadding(dp(6),dp(5),dp(6),dp(5));panel.addView(text);return text;}
    private void button(LinearLayout panel,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setTextSize(17);button.setTextColor(Color.WHITE);button.setAllCaps(false);button.setMinHeight(dp(48));button.setBackgroundResource(R.drawable.focus_button);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMargins(dp(4),dp(5),dp(4),dp(5));panel.addView(button,params);button.setOnClickListener(view->action.run());buttons.add(button);}
}
