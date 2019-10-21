package org.jboss.gm.cli;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import lombok.Getter;

import org.commonjava.maven.ext.common.ManipulationException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import ch.qos.logback.classic.Level;

import static org.apache.commons.lang.StringUtils.isNotBlank;

@SuppressWarnings("unused")
@Command(name = "GradleAnalyser",
        description = "CLI to optionally run Groovy scripts and then invoke Gradle.",
        mixinStandardHelpOptions = true, // add --help and --version options
        versionProvider = ManifestVersionProvider.class)
public class Main implements Callable<Void> {

    private GradleConnector connector = GradleConnector.newConnector();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Option(names = "-d", description = "Enable debug.")
    private boolean debug;

    @Option(names = { "-t", "--target" }, required = true, description = "Target Gradle directory.")
    private File target;

    @Option(names = { "-g", "--groovy" }, description = "Run a groovy script.")
    private String groovy;

    @Option(names = "-l", description = "Location of Gradle installation.")
    private File installation;

    @Parameters(description = "Anything following '--' is used as arguments to the Gradle process.")
    private List<String> gradleArgs;

    Main() {
    }

    private void executeGradle() throws ManipulationException {

        if (installation != null) {
            if (!installation.exists()) {
                throw new ManipulationException("Unable to locate Gradle installation at " + installation);
            }
            connector.useInstallation(installation);
        } else {
            connector.useBuildDistribution();
        }

        ProjectConnection connection = connector.connect();
        BuildLauncher build = connection.newBuild();

        logger.info("Executing Gradle project {} with arguments '{}'", target, gradleArgs);

        build.withArguments(gradleArgs);
        build.setColorOutput(true);
        build.setStandardOutput(System.out);
        build.setStandardError(System.err);

        build.run();
        connection.close();
    }

    int run(String[] args) throws Exception {
        CommandLine cl = new CommandLine(this);
        ExceptionHandler handler = new ExceptionHandler();

        cl.setExecutionStrategy(new CommandLine.RunAll());
        cl.setExecutionExceptionHandler(handler);

        int result = cl.execute(args);
        if (handler.getException() != null) {
            throw handler.getException();
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Main m = new Main();
        System.exit(m.run(args));
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     */
    @Override
    public Void call() throws ManipulationException {

        logger.info("### Inside call {} with debug {} ", gradleArgs, debug);

        final Logger rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) rootLogger;

        if (debug) {
            root.setLevel(Level.DEBUG);
        }

        if (target == null || !target.exists()) {
            throw new ManipulationException("Unable to locate target directory: " + target);
        }

        if (isNotBlank(groovy)) {
            logger.debug("### groovy {}", groovy);

            // TODO: Implement processing of 'first' groovy script.
            //   To consider:
            //      What are valid values (i.e. the Model won't be, but the Gradle files are).
            //      Should we duplicate the PME model of FIRST/LAST/BOTH?

        }

        connector.forProjectDirectory(target);
        executeGradle();

        return null;
    }

    @Getter
    private static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {

        private Exception exception;

        /**
         * Handles an {@code Exception} that occurred while executing the {@code Runnable} or
         * {@code Callable} command and returns an exit code suitable for returning from execute.
         *
         * @param ex the Exception thrown by the {@code Runnable}, {@code Callable} or {@code Method} user object of the command
         * @param commandLine the CommandLine representing the command or subcommand where the exception occurred
         * @param parseResult the result of parsing the command line arguments
         * @return an exit code
         */
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
            this.exception = ex;
            return 1;
        }
    }
}