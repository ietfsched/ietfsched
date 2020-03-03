/*
 * Copyright 2011 Isabelle Dalmasso.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ietf.ietfsched.io;

import android.util.Log;

import java.util.*;
import java.text.*;

import org.ietf.ietfsched.util.UIUtils;


public class Meeting {
	
	final static SimpleDateFormat previousFormat = new SimpleDateFormat("yyyy-MM-dd HHmm"); // 2011-07-23 0900
		// Hack: timezone format (Z) = +0800 where the ietfsched application expects +08:00. 
	final static SimpleDateFormat afterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:00.000", Locale.US);
	
	String day = " "; // Saturday, March, 12
	String startHour = " "; //2010-05-19T10:45:00.000-07:00
	String endHour = " "; // 2010-05-19T11:45:00.000-07:00
	String title = " ";
	String hrefDetail = " "; 
	String location = "N/A"; // room
	String group = " "; // APP
	String area = " "; // apparea
	String typeSession = ""; // Morning Session I
	String key = ""; // unique identifier

	static {
		previousFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE); 
		afterFormat.setTimeZone(UIUtils.CONFERENCE_TIME_ZONE);
		}
	
	public Meeting(String lineCsv) throws Exception {
		try {
			String[] splitted = lineCsv.split(",");
			Log.w("SplitCSV", "Split to" + splitted.toString() + " and length: " +splitted.length);
			// Each line element is now: b'things thangs'
			// remove the single quotes AND the leading b.
			for (int i = 0; i < splitted.length; i++){
				// Sometimes there is no content in an element, skip reparsing if so.
				if (splitted[i].contains("b'")) {
					Log.w("Prior regex", "Prior to regex: " + splitted[i]);
					splitted[i] = splitted[i].replaceAll("^\"b['\"](.*)['\"]\"", "$1");
					// splitted[i] = splitted[i].substring(2, splitted[i].length() - 1);
					Log.w("Post regex", "Post to regex: " + splitted[i]);
				}
			}
			day = splitted[0];
			startHour = convert(day, splitted[1]);
			endHour = convert(day, splitted[2]);
			typeSession = splitted[3];
			String tLocation = splitted[4].trim();
			if (tLocation.length() > 0) {
				location = tLocation;
			}
			group = splitted[5];
			area = splitted[6];
			title = splitted[8];
			key = splitted[9];
			hrefDetail = splitted[10];
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	private static String convert(String date, String hour) throws Exception {
	    Log.w("Convert", "Date/Hour: "+ date + "/"+hour);
		Date d = previousFormat.parse(String.format("%s %s", date, hour));
		Log.w("D", "FOO"+d.toString()+"FOO");
		return afterFormat.format(d);
	}
		
	public String toString() {
		return String.format("[%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s] [%s]", day, startHour, endHour, title, hrefDetail, location, group, area, typeSession, key);
		}
	}
	
