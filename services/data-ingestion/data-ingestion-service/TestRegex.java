import java.util.regex.*;

public class TestRegex {
    public static void main(String[] args) {
        String log = "syslog_version=1.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=10.0.0.1,response_port=80,line_id=1,Iface_type=1,Vlan_id=100,log_time=1693526400,eth_type=2048,ip_type=4";
        
        Pattern pattern = Pattern.compile(
            "^syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
            "dev_serial=([0-9A-Fa-f]+)\\s*,\\s*" +
            "log_type=(\\d+)\\s*,\\s*" +
            "sub_type\\s*=\\s*(\\d+)\\s*,\\s*" +
            "attack_mac=([0-9A-Fa-f:]+)\\s*,\\s*" +
            "attack_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
            "response_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
            "response_port=(\\d+)\\s*,\\s*" +
            "line_id=(\\d+)\\s*,\\s*" +
            "Iface_type=(\\d+)\\s*,\\s*" +
            "Vlan_id=(\\d+)\\s*,\\s*" +
            "log_time=(\\d+)\\s*,\\s*" +
            "eth_type\\s*=\\s*(\\d+)\\s*,\\s*" +
            "ip_type\\s*=\\s*(\\d+)$"
        );
            
        Matcher matcher = pattern.matcher(log);
        System.out.println("Log: " + log);
        System.out.println("Matches: " + matcher.find());
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                System.out.println("Group " + i + ": " + matcher.group(i));
            }
        }
    }
}