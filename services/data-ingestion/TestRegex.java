import java.util.regex.*;

public class TestRegex {
    public static void main(String[] args) {
        // 测试实际的日志内容
        String log = "syslog_version=1.10.0,dev_serial=9d262111f2476d34,log_type=1,sub_type =1,attack_mac=10:e9:53:d4:67:a0,attack_ip=122.225.36.34,response_ip=192.168.2.199,response_port=50376,line_id=1,Iface_type=1,Vlan_id=0,log_time=1714286129,eth_type =2048,ip_type = 6";
        
        // 使用LogParserService中的模式
        Pattern pattern = Pattern.compile(
            "(?:<\\d+>\\w+\\s+\\d+\\s+\\d+:\\d+:\\d+\\s+[^\\s]+\\s+[^:]+:\\s*)?" +  // 可选的syslog头部
            "syslog_version=(\\d+(?:\\.\\d+)*)\\s*,\\s*" +
            "dev_serial=([0-9A-Fa-f]+)\\s*,\\s*" +
            "log_type=(\\d+)\\s*,\\s*" +
            "sub_type\\s*=\\s*(\\d+)\\s*,\\s*" +
            "attack_mac=([0-9A-Fa-f:]+)\\s*,\\s*" +
            "attack_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
            "response_ip=((?:\\d{1,3}\\.){3}\\d{1,3})\\s*,\\s*" +
            "response_port=(-?\\d+)\\s*,\\s*" +
            "line_id=(\\d+)\\s*,\\s*" +
            "Iface_type=(\\d+)\\s*,\\s*" +
            "Vlan_id=(\\d+)\\s*,\\s*" +
            "log_time=(\\d+)\\s*,\\s*" +
            "eth_type\\s*=\\s*(\\d+)\\s*,\\s*" +
            "ip_type\\s*=\\s*(\\d+)$"
        );
            
        Matcher matcher = pattern.matcher(log);
        System.out.println("Log: " + log);
        System.out.println("Matches: " + matcher.matches());
        if (matcher.matches()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                System.out.println("Group " + i + ": " + matcher.group(i));
            }
        } else {
            System.out.println("No match found");
        }
    }
}