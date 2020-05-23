package net.ghiassy.simpleftpclient;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpProgressMonitor;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.io.CopyStreamAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "net.ghiassy.simpleftpclient.MainActivity";

    TextView  txtUsername, txtPassword, txtFilePath;
    AutoCompleteTextView txtServerAddress;

    Button btnUpload;
    ProgressBar progressBar;
    CheckBox checkBox;

    ArrayList<String> ConnectedServers = new ArrayList();
    ArrayAdapter<String> adapter;

    private String Protocol;
    AdView ads1;

    SharedPreferences sharedPreferences;
    String FilePath;

    long CountBytes;


    //================================== FTP Uploader Inner Class =============================================
    public class FTPUploader extends AsyncTask<String, String , String>
    {


        // Update the GUI based on transferred bytes
        @Override
        protected void onProgressUpdate(String... values) {

            txtFilePath.setText("\n[+]Filename: " + values[0] + "   Uploaded: %" + values[1]);
            progressBar.setProgress(Integer.parseInt(values[1]));
        }

        @Override
        protected String doInBackground(String... strings) {

            Handler handler=  new Handler(getApplicationContext().getMainLooper());
            if(strings.length !=4)
            {
                // This is the way to call the Toast from outside the UI class

                handler.post( new Runnable(){
                    public void run(){
                        Toast.makeText(getApplicationContext(), "Please provide all the information.",Toast.LENGTH_LONG).show();
                    }
                });
                return "";
            }
            String IP = strings[0];
            String Username = strings[1];
            String Password = strings[2];
            String Filename = strings[3];

            txtFilePath = findViewById(R.id.txtFileLocation);
            progressBar = findViewById(R.id.progressBarUpload);

            final File file = new File(Filename);

            FTPClient ftpClient = new FTPClient();
            try
            {
                ftpClient.setConnectTimeout(5000);
                ftpClient.connect(IP, 21);

                if(!ftpClient.login(Username, Password))
                {
                    handler.post( new Runnable(){
                        public void run(){
                            Toast.makeText(getApplicationContext(), "Error: Invalid username or password ",Toast.LENGTH_LONG).show();
                        }
                    });
                }
                if(ftpClient.isConnected())
                {
                    ConnectedServers.add(IP);
                    ConnectedServers = removeDuplicates(ConnectedServers);
                    saveArrayList(ConnectedServers, "Servers");
                }
                ftpClient.enterLocalPassiveMode();
                ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);

                //Apache FTPClinet library
                //This listeners helps to monitor transferred bytes to update progress bar

                CopyStreamAdapter streamListener = new CopyStreamAdapter() {

                    @Override
                    public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                        //this method will be called everytime some bytes are transferred

                        int percent = (int)(totalBytesTransferred*100/file.length());
                        // update your progress bar with this percentage
                        txtFilePath.setText("\n[+]Filename: " + file.getName() + "   Uploaded: %" + percent);
                        progressBar.setProgress(percent);
//                        publishProgress( file.getName() , String.valueOf(percent));
                    }

                };
                ftpClient.setCopyStreamListener(streamListener);

                InputStream inputStream = new FileInputStream(Filename);
                boolean done = ftpClient.storeFile(file.getName(), inputStream);
                inputStream.close();
                if (done) {
                    txtFilePath.setText("\n[!]Status: Upload completed.");
                }

            }catch (FTPConnectionClosedException e)
            {
                e.printStackTrace();
                handler.post( new Runnable(){
                    public void run(){
                        Toast.makeText(getApplicationContext(), "FTP: Error, Please check your input.",Toast.LENGTH_LONG).show();
                    }
                });
            }catch(Exception e)
            {
                e.printStackTrace();

            }finally {
                try {
                    if (ftpClient.isConnected())
                    {

                        ftpClient.logout();
                        ftpClient.disconnect();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            return "Done!";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i("Uploader: " , "Upload Completed.");

            adapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_dropdown_item_1line, ConnectedServers);
            txtServerAddress.setAdapter(adapter);

        }
    }
    //================================== END of FTP Uploader Inner Class =======================================


    //================================== SFTP Uploader Inner Class =============================================
    //Library implementation 'com.jcraft:jsch:0.1.55' is needed
    public class SFTPUploader extends AsyncTask<String, String , String>
    {
        String Filename;
        @Override
        protected void onProgressUpdate(String... values) {

            txtFilePath.setText("\n[+]Filename: " + values[0] + "   Uploaded: %" + values[1]);
            progressBar.setProgress(Integer.parseInt(values[1]));
        }

        @Override
        protected String doInBackground(String... strings) {

            Handler handler=  new Handler(getApplicationContext().getMainLooper());
            if(strings.length !=4)
            {
                // This is the way to call the Toast from outside the UI class

                handler.post( new Runnable(){
                    public void run(){
                        Toast.makeText(getApplicationContext(), "Please provide all the information.",Toast.LENGTH_LONG).show();
                    }
                });
                return "";
            }
            String IP = strings[0];
            String Username = strings[1];
            String Password = strings[2];
            Filename = strings[3];

            txtFilePath = findViewById(R.id.txtFileLocation);
            progressBar = findViewById(R.id.progressBarUpload);
            CountBytes=0;

            final File file = new File(Filename);
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(Username, IP, 22);
                session.setPassword(Password);

                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setTimeout(5000);
                session.connect();

                if(session.isConnected())
                {
                    ConnectedServers.add(IP);
                    ConnectedServers = removeDuplicates(ConnectedServers);
                    saveArrayList(ConnectedServers, "Servers");
                }

                Channel channel = session.openChannel("sftp");
                channel.connect();
                ChannelSftp channelSftp = (ChannelSftp) channel;

                //Listener to monitor transferred bytes

                SftpProgressMonitor progress = new SftpProgressMonitor() {
                    @Override
                    public void init(int arg0, String arg1, String arg2, long arg3)
                    {
                        Log.i(TAG, " SFTP File transfer begin..");
                    }

                    @Override
                    public void end()
                    {
                        txtFilePath.setText("\n[!]Status: Upload completed.");
                        progressBar.setProgress(100);
                    }

                    @Override
                    public boolean count(long i)
                    {

                        CountBytes += i;
                        int percent = (int)(CountBytes*100/file.length());

                        publishProgress( file.getName() , String.valueOf(percent));
//                        txtFilePath.setText("\n[+]Filename: " + file.getName() + "   Uploaded: %" + percent);
//                        progressBar.setProgress(percent);
                        return true;
                    }
                };
                channelSftp.put(new FileInputStream(file), file.getName(), progress);

                txtFilePath.setText("\n[!]Status: Upload completed.");
                progressBar.setProgress(100);

            }catch(JSchException e)
            {
                handler.post( new Runnable(){
                    public void run(){
                        Toast.makeText(getApplicationContext(), "SFTP: Error, Please check your input.",Toast.LENGTH_LONG).show();
                    }
                });
            }catch (Exception ex) {
                ex.printStackTrace();
            }

            return "Done!";
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.i(TAG , "SFTP Upload Completed.");
            adapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_dropdown_item_1line, ConnectedServers);
            txtServerAddress.setAdapter(adapter);

        }
    }
    //================================== END of SFTP Uploader Inner Class =======================================


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "NO Permission!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = this.getSharedPreferences("net.ghiassy.simpleftpclient", Context.MODE_PRIVATE);

        Spinner spinner = findViewById(R.id.spinner);

        txtServerAddress = findViewById(R.id.autoTxtServerAddr);
        txtUsername = findViewById(R.id.txtUsername);
        txtPassword = findViewById(R.id.txtPassword);
        txtFilePath = findViewById(R.id.txtFileLocation);
        btnUpload = findViewById(R.id.btnUpload);
        checkBox = findViewById(R.id.checkBox);

        ads1 = findViewById(R.id.adView);

        checkBox.setChecked(sharedPreferences.getBoolean("Remember" , false));

        if(checkBox.isChecked())
        {
            txtUsername.setText(sharedPreferences.getString("Username", ""));
            txtPassword.setText(sharedPreferences.getString("Password", ""));
        }

        ArrayAdapter<CharSequence> arrayAdapter = ArrayAdapter.createFromResource(this,
                R.array.Protocols,
                android.R.layout.simple_spinner_item);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Protocol = parent.getItemAtPosition(position).toString();
                //Toast.makeText(parent.getContext(), Protocol, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        //Auto completion suggestions
        ConnectedServers = getArrayList("Servers");
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line, ConnectedServers);
        txtServerAddress.setAdapter(adapter);


        //==================== GOOGLE ADS and permissions ======================================
        MobileAds.initialize(this,
                "ca-app-pub-8053134103811321~9565106740");
        AdRequest adRequest = new AdRequest.Builder().build();
        ads1.loadAd(adRequest);


        if(Build.VERSION.SDK_INT >= 23  && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1000);
        }
        //=======================================================================================

    }

    //================================= Menu ======================================================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
            super.onOptionsItemSelected(item);

        if(item.getItemId() == R.id.menu_itm_clear)
        {
            Toast.makeText(this, "Clear history", Toast.LENGTH_SHORT).show();
            ConnectedServers.clear();
            saveArrayList(ConnectedServers, "Servers");

            adapter = new ArrayAdapter<String>(this,android.R.layout.simple_dropdown_item_1line, ConnectedServers);
            txtServerAddress.setAdapter(adapter);
        }

        return false;

    }
    //================================= End of Menu ======================================================


    public void btnUploadClicked(View view)
    {
        btnUpload.setEnabled(false);

        if(Protocol.equals("FTP")) {
            FTPUploader uploader = new FTPUploader();
            uploader.execute(txtServerAddress.getText().toString(), txtUsername.getText().toString(), txtPassword.getText().toString(), FilePath);
        }else{
           // Toast.makeText(this, "SFTP", Toast.LENGTH_SHORT).show();
            SFTPUploader uploader = new SFTPUploader();
            uploader.execute(txtServerAddress.getText().toString(), txtUsername.getText().toString(), txtPassword.getText().toString(), FilePath);

        }

    }


    public void btnFileChooserClicked(View view)
    {
        Intent myFileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        myFileIntent.setType("*/*");
        Intent.createChooser(myFileIntent, "Choose a file");
        startActivityForResult(myFileIntent, 10);

    }

    public void checkBoxClicked(View view)
    {
        if(checkBox.isChecked())
        {
            sharedPreferences.edit().putBoolean("Remember" , true).apply();
            sharedPreferences.edit().putString("Username" , txtUsername.getText().toString()).apply();
            sharedPreferences.edit().putString("Password" ,  txtPassword.getText().toString()).apply();

        }else{
            sharedPreferences.edit().putBoolean("Remember" , false).apply();
        }

    }


    //Saving ArrayList to SharedPreferences
    public void saveArrayList(ArrayList<String> list, String key){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(list);
        editor.putString(key, json);
        editor.apply();     // This line is IMPORTANT !!!
    }

    //Retrieve  ArrayList to SharedPreferences
    public ArrayList<String> getArrayList(String key){
        Gson gson = new Gson();
        String json = sharedPreferences.getString(key, null);
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // Remove duplicated Strings from the Connections ArrayList
    public ArrayList<String> removeDuplicates(ArrayList<String> list)
    {
        Set<String> hashSet = new LinkedHashSet(list);
        ArrayList<String> removedDuplicates = new ArrayList(hashSet);
        return removedDuplicates;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);


        switch (requestCode){
            case 10:
                if(resultCode== RESULT_OK)
                {
                    Uri fileUri = data.getData();

                    FilePath = FileUtils.getPath(this, fileUri);

                    File file =  new File(FilePath);
                    if(file.exists())
                    {
                        btnUpload.setEnabled(true);
                        txtFilePath.setText("[+]File to location to upload: \n" +file.getPath());

                    }else{
                        Toast.makeText(this, "File cannot be added!", Toast.LENGTH_SHORT).show();
                        txtFilePath.setText("[!]File cannot be added :(");
                        btnUpload.setEnabled(false);
                    }

                }

                break;
        }

    }


}
