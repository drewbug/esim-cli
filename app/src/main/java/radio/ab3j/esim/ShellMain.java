package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SmsManager;
import com.genymobile.scrcpy.Ln;

public class ShellMain {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: sh -s SEND_SMS <phone_number> <message>");
            System.exit(1);
        }
        
        String command = args[0];
        
        if ("SEND_SMS".equals(command)) {
            if (args.length < 3) {
                System.err.println("Error: SEND_SMS requires phone number and message");
                System.err.println("Usage: sh -s SEND_SMS <phone_number> <message>");
                System.exit(1);
            }
            
            String phoneNumber = args[1];
            String message = args[2];
            
            try {
                SmsManager smsManager = ServiceManager.getSmsManager();
                smsManager.sendTextMessage(phoneNumber, message);
                System.out.println("SMS sent successfully to " + phoneNumber);
            } catch (Exception e) {
                System.err.println("Failed to send SMS: " + e.getMessage());
                Ln.e("SMS send error", e);
                System.exit(1);
            }
        } else {
            System.err.println("Unknown command: " + command);
            System.err.println("Available commands: SEND_SMS");
            System.exit(1);
        }
    }
}