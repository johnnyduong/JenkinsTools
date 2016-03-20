/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.jenkins.tools;

import java.io.Console;
import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.Option;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * @author Kevin Yen
 */
public class JenkinsStatus {

	private static final Logger logger = (Logger) LoggerFactory.getLogger(JenkinsStatus.class);

	private static final int THREAD_POOL_SIZE = 120;

	private JsonGetter jsonGetter = new LocalJsonGetter();

	private boolean matchBuilding = true;

	private boolean matchNotBuilding = false;

	private Pattern pattern = Pattern.compile(".*");

	private Map<String, String> matchParameters = new HashMap<>();

	private void processArgs(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();

		Options options = new Options();

		options.addOption("i", "info", false, "Set logging level to info.");
		options.addOption("d", "debug", false, "Set logging level to debug.");
		options.addOption("u", "user", true, "Specify the username used in authentication.");
		options.addOption("n", "name", true, "Regular expression used to match job name.");
		options.addOption("b", "building", true, "Specify the state of the build to match: true, false, or any");
		options.addOption(
			Option.builder("p")
			.longOpt("parameter")
			.hasArgs()
			.desc("Specify the parameter of the build to match")
			.valueSeparator(',')
			.build());

		CommandLine line = parser.parse(options, args);

		if (line.hasOption("i")) {
			Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			rootLogger.setLevel(Level.INFO);
		}

		if (line.hasOption("d")) {
			Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
			rootLogger.setLevel(Level.DEBUG);

			logger.debug("Checking for the following options: {}", options.toString());
		}

		if (line.hasOption("n")) {
			logger.debug("Using the regular expression pattern {} to match job name", line.getOptionValue("n"));

			pattern = Pattern.compile(line.getOptionValue("n"));
		}

		if (line.hasOption("u")) {
			Console console = System.console();

			if (console == null) {
				logger.error("Unable to get Console instance.");

				throw new IllegalStateException("Unable to get Console instance.");
			}

			String username = line.getOptionValue("u");
			String password = new String(console.readPassword("Enter password for " + username + " :"));

			jsonGetter = new RemoteJsonGetter(username, password);
		}

		if (line.hasOption("b")) {
			if (line.getOptionValue("b").equals("true")) {
				matchBuilding = true;
				matchNotBuilding = false;
			}
			if (line.getOptionValue("b").equals("false")) {
				matchBuilding = false;
				matchNotBuilding = true;
			}
			if (line.getOptionValue("b").equals("any")) {
				matchBuilding = true;
				matchNotBuilding = true;
			}
		}

		if (line.hasOption("p")) {
			for (String parameterString : line.getOptionValues("p")) {
				String[] parameterSet = parameterString.split("=");

				if (parameterSet.length != 2) {
					logger.error("Invalid parameter format");

					throw new IllegalArgumentException("Invalid parameter format");
				}

				matchParameters.put(parameterSet[0], parameterSet[1]);
			}

			logger.debug("Matching parameters {}", matchParameters.toString());
		}
	}

	public void listBuilds() throws Exception {
		Set<String> jenkinsURLs = new HashSet<>();

		for (int i = 1; i <= 20; i++) {
			String jenkinsURL = JenkinsJobURLs.getJenkinsURL(i, false);

			jenkinsURL = jsonGetter.convertURL(jenkinsURL);

			jenkinsURLs.add(jenkinsURL);

			logger.info("Adding {} to the list of servers to search.", jenkinsURL);
		}

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

		Set<JenkinsBuild> matchingJenkinsBuilds = new HashSet<>();

		try {
			Set<JenkinsJob> jenkinsJobs = JenkinsJobsGetter.getJenkinsJobs(jsonGetter, executor, jenkinsURLs);

			Set<JenkinsJob> matchingJenkinsJobs = new HashSet<>();

			for (JenkinsJob jenkinsJob : jenkinsJobs) {
				Matcher matcher = pattern.matcher(jenkinsJob.getName());

				if (matcher.find()) {
					matchingJenkinsJobs.add(jenkinsJob);

					logger.info("Found job {} matching regular expression", jenkinsJob.getName());
				}
			}

			logger.info("Found {} jobs matching regular expression", matchingJenkinsJobs.size());

			Set<JenkinsBuild> jenkinsBuilds = JenkinsBuildsGetter.getJenkinsBuilds(jsonGetter, executor, matchingJenkinsJobs);

			for (JenkinsBuild jenkinsBuild : jenkinsBuilds) {
				if (jenkinsBuild.isBuilding() && matchBuilding) {
					matchingJenkinsBuilds.add(jenkinsBuild);
				}
				if (!jenkinsBuild.isBuilding() && matchNotBuilding) {
					matchingJenkinsBuilds.add(jenkinsBuild);
				}
			}
		}
		catch (ExecutionException e) {
			e.printStackTrace();

			throw e;
		}
		finally {
			System.out.println("Found " + matchingJenkinsBuilds.size() + " builds");

			for (JenkinsBuild activeJenkinsBuild : matchingJenkinsBuilds){
				System.out.println(activeJenkinsBuild.getURL());

				Map<String, String> parameters = activeJenkinsBuild.getParameters();

				for (String name : parameters.keySet()) {
					logger.info("{}={}", name, parameters.get(name));
				}
			}

			executor.shutdown();
		}
	}

	public static void main(String [] args) throws Exception {
		JenkinsStatus jenkinsStatus = new JenkinsStatus();

		jenkinsStatus.processArgs(args);

		jenkinsStatus.listBuilds();
	}

}