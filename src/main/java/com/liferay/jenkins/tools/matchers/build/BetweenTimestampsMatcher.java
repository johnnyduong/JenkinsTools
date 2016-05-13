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

import java.text.DateFormat;
import java.text.ParseException;

import java.util.Calendar;
import java.util.Date;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;

/**
 * @author Kevin Yen
 */
public class BetweenTimestampsMatcher extends TimestampMatcher {

	private static final Logger logger = (Logger) LoggerFactory.getLogger(
		BetweenTimestampsMatcher.class);

	Date end = new Date(Long.MAX_VALUE);
	Date start = new Date(Long.MIN_VALUE);

	public BetweenTimestampsMatcher(String timestamp1, String timestamp2)
		throws IllegalArgumentException {

		this(parseTimestamp(timestamp1), parseTimestamp(timestamp2));
	}

	public BetweenTimestampsMatcher(long timestamp1, long timestamp2) {
		this(new Date(timestamp1), new Date(timestamp2));
	}

	public BetweenTimestampsMatcher(Date timestamp1, Date timestamp2) {
		if (timestamp1.before(timestamp2)) {
			this.start = timestamp1;
			this.end = timestamp2;
		}
		else {
			this.start = timestamp2;
			this.end = timestamp1;
		}

		logger.debug("Matching builds between {} and {}", start, end);
	}

	@Override
	public boolean matches(Build jenkinsBuild) {
		Date date = new Date(jenkinsBuild.getTimestamp());

		if (date.after(start) && date.before(end)) {
			return true;
		}

		return false;
	}

}