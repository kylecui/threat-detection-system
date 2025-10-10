import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestRegex {
    public static void main(String[] args) {
        String testLog = "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type =1,attack_mac=e0:4f:43:29:8c:eb,attack_ip=192.168.16.159,response_ip=192.168.16.137,response_port=65536,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747276019";
        
        System.out.println("Testing log: " + testLog);
        System.out.println();
        
        // 测试 dev_serial 字段的具体匹配
        Pattern devSerialPattern = Pattern.compile("dev_serial=([0-9A-Fa-f]+)");
        Matcher devSerialMatcher = devSerialPattern.matcher(testLog);
        System.out.println("Testing dev_serial pattern: " + devSerialPattern.pattern());
        
        boolean found = false;
        while (devSerialMatcher.find()) {
            System.out.println("dev_serial matched at position " + devSerialMatcher.start() + ": '" + devSerialMatcher.group(1) + "'");
            found = true;
        }
        if (!found) {
            System.out.println("dev_serial NOT matched with find()");
            
            // 尝试手动查找
            String searchStr = "dev_serial=";
            int pos = testLog.indexOf(searchStr);
            if (pos != -1) {
                System.out.println("Found 'dev_serial=' at position: " + pos);
                int endPos = testLog.indexOf(",", pos);
                if (endPos != -1) {
                    String value = testLog.substring(pos + searchStr.length(), endPos);
                    System.out.println("Extracted value: '" + value + "'");
                    
                    // 检查字符是否都是有效的十六进制
                    boolean allHex = true;
                    for (char c : value.toCharArray()) {
                        if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
                            System.out.println("Invalid hex char: " + c + " (code: " + (int)c + ")");
                            allHex = false;
                        }
                    }
                    System.out.println("All characters valid hex: " + allHex);
                }
            }
        }
    }
}
