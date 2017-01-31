package plugins;

import com.google.inject.Inject;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.XvncSlaveContainer;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.DockerTest;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.ssh_slaves.SshSlaveLauncher;
import org.jenkinsci.test.acceptance.plugins.xvnc.XvncGlobalJobConfig;
import org.jenkinsci.test.acceptance.plugins.xvnc.XvncJobConfig;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.DumbSlave;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.jvnet.hudson.test.Issue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.jenkinsci.test.acceptance.plugins.xvnc.XvncJobConfig.runXvnc;
import static org.jenkinsci.test.acceptance.plugins.xvnc.XvncJobConfig.tookScreenshot;
import static org.jenkinsci.test.acceptance.plugins.xvnc.XvncJobConfig.usedDisplayNumber;

@WithPlugins({"xvnc", "ssh-slaves"})
@Category(DockerTest.class)
@WithDocker
public class XvncPluginTest extends AbstractJUnitTest {

    public static final String REMOTE_FS = "/tmp";

    @Inject DockerContainerHolder<XvncSlaveContainer> docker;

    private XvncSlaveContainer sshd;
    private DumbSlave slave;

    @Before public void setUp() {
        sshd = docker.get();

        slave = jenkins.slaves.create(DumbSlave.class);
        slave.setExecutors(1);
        slave.setLabels("xvnc");
        slave.remoteFS.set(REMOTE_FS);
        configureDefaultSSHSlaveLauncher()
                .pwdCredentials("test", "test");
        slave.save();
    }

    private SshSlaveLauncher configureDefaultSSHSlaveLauncher() {
        return configureSSHSlaveLauncher(sshd.ipBound(22), sshd.port(22));
    }

    private SshSlaveLauncher configureSSHSlaveLauncher(String host, int port) {
        SshSlaveLauncher launcher = slave.setLauncher(SshSlaveLauncher.class);
        launcher.host.set(host);
        launcher.port(port);
        return launcher;
    }

    private FreeStyleJob createJob() {
        FreeStyleJob job = jenkins.jobs.create(FreeStyleJob.class);
        job.configure();
        job.setLabelExpression("xvnc");
        return job;
    }

    @Test
    public void run_xvnc_during_the_build() {
        FreeStyleJob job = createJob();
        new XvncJobConfig(job).useXvnc();
        job.save();

        Build build = job.startBuild().shouldSucceed();
        assertThat(build, runXvnc());
    }

    @Test
    public void take_screenshot_at_the_end_of_the_build() {
        FreeStyleJob job = createJob();
        new XvncJobConfig(job).useXvnc().takeScreenshot();
        job.save();

        Build build = job.startBuild().shouldSucceed();
        assertThat(build, runXvnc());
        assertThat(build, tookScreenshot());
        build.getArtifact("screenshot.jpg").assertThatExists(true);
    }

    @Test
    public void use_specific_display_number() {
        jenkins.configure();
        new XvncGlobalJobConfig(jenkins.getConfigPage())
                .useDisplayNumber(42)
        ;
        jenkins.save();

        FreeStyleJob job = createJob();
        new XvncJobConfig(job).useXvnc();
        job.save();

        Build build = job.startBuild().shouldSucceed();
        assertThat(build, runXvnc());
        assertThat(build, usedDisplayNumber(42));
    }

    @WithPlugins({"xvnc@1.22", "workflow-aggregator@1.8"})
    @Issue("JENKINS-26477")
    @Test public void workflow() {
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.script.set("node('xvnc') {wrap([$class: 'Xvnc', takeScreenshot: true, useXauthority: true]) {sh 'xmessage hello &'}}");
        job.sandbox.check();
        job.save();
        Build build = job.startBuild().shouldSucceed();
        assertThat(build.getConsole(), containsString("+ xmessage hello"));
        assertThat(build, runXvnc());
        assertThat(build, tookScreenshot());
        build.getArtifact("screenshot.jpg").assertThatExists(true); // TODO should this be moved into tookScreenshot?
    }

}
