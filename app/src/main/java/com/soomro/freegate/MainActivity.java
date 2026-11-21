package com.soomro.freegate;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import android.util.Base64;

public class MainActivity extends Activity {
  private final ArrayList<Server> servers = new ArrayList<>();
  private LinearLayout list;
  private TextView status;
  private EditText filter;
  private byte[] pendingProfile;
  private static final int SAVE_PROFILE = 21;
  static class Server {
    String country, host, ip, profile;
    int speed, ping;
  }
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(22,28,22,12); root.setBackgroundColor(Color.rgb(246,248,252));
    TextView title = new TextView(this); title.setText("FreeGate"); title.setTextSize(28); title.setTypeface(null, Typeface.BOLD); root.addView(title);
    TextView hint = new TextView(this); hint.setText("Free volunteer servers from VPN Gate. Import a profile into OpenVPN Connect to connect."); root.addView(hint);
    filter = new EditText(this); filter.setSingleLine(true); filter.setHint("Filter by country"); root.addView(filter);
    Button refresh = new Button(this); refresh.setText("Refresh servers"); root.addView(refresh);
    status = new TextView(this); status.setText("Loading servers..."); root.addView(status);
    ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
    list = new LinearLayout(this); list.setOrientation(1); scroll.addView(list);
    setContentView(root);
    refresh.setOnClickListener(v -> load());
    filter.addTextChangedListener(new android.text.TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int c,int f) {} public void onTextChanged(CharSequence s,int a,int b,int c) { render(); } public void afterTextChanged(android.text.Editable e) {} });
    load();
  }
  private void load() {
    status.setText("Loading VPN Gate servers...");
    new Thread(() -> {
      ArrayList<Server> found = new ArrayList<>();
      try {
        URL url = new URL("https://www.vpngate.net/api/iphone/");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection(); conn.setConnectTimeout(12000); conn.setReadTimeout(20000); conn.setRequestProperty("User-Agent", "FreeGate-Android/1.0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8))) {
          String line;
          while ((line=reader.readLine())!=null) {
            if (line.startsWith("#") || line.startsWith("*") || line.trim().isEmpty()) continue;
            String[] cols=line.split(",",15); if(cols.length!=15 || !cols[0].startsWith("vpn")) continue;
            Server s=new Server(); s.host=cols[0]; s.ip=cols[1]; s.country=cols[5]; s.speed=parseInt(cols[4]); s.ping=parseInt(cols[3]); s.profile=cols[14].trim();
            if (!s.profile.isEmpty()) found.add(s);
          }
        } finally { conn.disconnect(); }
        found.sort((a,b)->Integer.compare(b.speed,a.speed));
        runOnUiThread(() -> { servers.clear(); servers.addAll(found); status.setText(found.size()+" OpenVPN servers available. Availability can change."); render(); });
      } catch(Exception ex) { runOnUiThread(() -> status.setText("Cannot load servers. Check your internet connection, then retry.")); }
    }).start();
  }
  private static int parseInt(String value) { try { return Integer.parseInt(value); } catch(Exception e) { return 0; } }
  private void render() {
    if(list==null || filter==null) return;
    list.removeAllViews(); int count=0;
    String query=filter.getText().toString().trim().toLowerCase(Locale.ROOT);
    for(Server s:servers) {
      if (!s.country.toLowerCase(Locale.ROOT).contains(query)) continue;
      if (++count>100) break;
      LinearLayout row=new LinearLayout(this); row.setOrientation(1); row.setPadding(12,12,12,12);
      TextView label=new TextView(this); label.setText(s.country+"  •  "+s.host+"\n"+(s.speed/1000000)+" Mbps  •  "+s.ping+" ms ping"); label.setTextSize(16); row.addView(label);
      Button save=new Button(this); save.setText("Save OpenVPN profile"); row.addView(save);
      save.setOnClickListener(v -> saveProfile(s)); list.addView(row);
      View divider=new View(this); divider.setBackgroundColor(Color.LTGRAY); list.addView(divider,new LinearLayout.LayoutParams(-1,1));
    }
    if(count==0 && !servers.isEmpty()) status.setText("No servers match this country.");
    else if (!servers.isEmpty()) status.setText(servers.size()+" OpenVPN servers available. Availability can change.");
  }
  private void saveProfile(Server s) {
    try {
      byte[] data=Base64.decode(s.profile,Base64.DEFAULT);
      String config=new String(data,StandardCharsets.UTF_8);
      if(data.length>256*1024 || !config.contains("client") || !config.contains("remote ")) throw new IllegalArgumentException("Invalid profile");
      pendingProfile=data;
      Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/x-openvpn-profile"); intent.putExtra(Intent.EXTRA_TITLE,"vpngate_"+s.ip.replace('.','_')+".ovpn");
      startActivityForResult(intent,SAVE_PROFILE);
    } catch(Exception e) { Toast.makeText(this,"This server profile is unavailable. Try another server.",Toast.LENGTH_LONG).show(); }
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request!=SAVE_PROFILE || result!=RESULT_OK || data==null || data.getData()==null || pendingProfile==null) return;
    try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
      if(out==null) throw new IOException("Cannot open file"); out.write(pendingProfile);
      Toast.makeText(this,"Profile saved. Open OpenVPN Connect, import this file, then connect. Username and password: vpn",Toast.LENGTH_LONG).show();
    } catch(Exception e) { Toast.makeText(this,"Could not save profile.",Toast.LENGTH_LONG).show(); }
    finally { pendingProfile=null; }
  }
}
