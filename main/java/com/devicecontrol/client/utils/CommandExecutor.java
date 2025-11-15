package com.devicecontrol.client.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class CommandExecutor {
    private static final String TAG = "CommandExecutor";
    private Context context;
    
    public CommandExecutor(Context context) {
        this.context = context;
    }
    
    public String executeShellCommand(String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing shell command: " + command, e);
            return "Error: " + e.getMessage();
        }
        
        return output.toString();
    }
    
    public boolean executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            
            process.waitFor();
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error executing root command: " + command, e);
            return false;
        }
    }
    
    public boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            
            process.waitFor();
            return process.exitValue() == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
}