import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

public final class VehicleClientTestRunner {
    public static void main(String[] args) throws Exception {
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        var request=LauncherDiscoveryRequestBuilder.request();
        for(int i=1;i<args.length;i++)request.selectors(DiscoverySelectors.selectClass(args[i]));
        launcher.execute(request.build());
        var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
        var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite tests=\"").append(summary.getTestsFoundCount())
                .append("\" failures=\"").append(summary.getTestsFailedCount()).append("\" skipped=\"").append(summary.getTestsSkippedCount()).append("\">");
        for(var failure:summary.getFailures())xml.append("<testcase name=\"").append(failure.getTestIdentifier().getDisplayName()).append("\"><failure><![CDATA[")
                .append(failure.getException().toString().replace("]]>","]] >")).append("]]></failure></testcase>");
        Files.writeString(Path.of(args[0]),xml.append("</testsuite>").toString());
        if(summary.getTestsFoundCount()==0||summary.getTestsFailedCount()>0||summary.getTestsSkippedCount()>0||summary.getContainersFailedCount()>0)System.exit(1);
    }
}
