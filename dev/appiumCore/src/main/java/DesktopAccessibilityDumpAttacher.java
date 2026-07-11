import com.sun.tools.attach.VirtualMachine;

public class DesktopAccessibilityDumpAttacher {
    public static void dump(String pid, String agentPath, String outputPath) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentPath, outputPath);
        } finally {
            vm.detach();
        }
    }
}
