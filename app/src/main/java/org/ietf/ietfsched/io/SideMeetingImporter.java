/*
 * Copyright 2026
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

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.ietf.ietfsched.provider.ScheduleContract.Blocks;
import org.ietf.ietfsched.provider.ScheduleContract.Rooms;
import org.ietf.ietfsched.provider.ScheduleContract.Sessions;
import org.ietf.ietfsched.util.Lists;
import org.ietf.ietfsched.util.ParserUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Imports informal side-meeting bookings from https://sidemeetings.ietf.org/_data
 * into the same blocks/rooms/sessions tables used by the agenda sync.
 */
public class SideMeetingImporter {
    private static final String TAG = "SideMeetingImporter";
    public static final String SIDE_MEETINGS_URL = "https://sidemeetings.ietf.org/_data";
    /** Short timeouts so a broken informal API never stalls agenda sync. */
    public static final int CONNECT_TIMEOUT_MS = 4000;
    public static final int READ_TIMEOUT_MS = 6000;

    private SideMeetingImporter() {}

    /**
     * Build insert ops for side meetings. Returns empty list on any problem.
     * Caller must use the same {@code versionBuild} as the agenda batch so purge keeps both.
     */
    public static ArrayList<ContentProviderOperation> buildOperations(
            JSONObject sideData, int expectedMeetingNumber, long versionBuild,
            ContentResolver resolver) {
        ArrayList<ContentProviderOperation> batch = Lists.newArrayList();
        if (sideData == null || sideData.length() == 0) {
            return batch;
        }
        try {
            JSONObject meeting = sideData.optJSONObject("meeting");
            if (meeting == null) {
                Log.w(TAG, "No meeting object in side meetings data");
                return batch;
            }
            String numberStr = meeting.optString("meetingNumber", "");
            int sideMeetingNumber;
            try {
                sideMeetingNumber = Integer.parseInt(numberStr.trim());
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid side meeting number: " + numberStr);
                return batch;
            }
            if (sideMeetingNumber != expectedMeetingNumber) {
                Log.i(TAG, "Skipping side meetings: data is for IETF " + sideMeetingNumber
                        + " but app meeting is " + expectedMeetingNumber);
                return batch;
            }

            HashMap<Long, Integer> roomSubColumn = assignRoomSubColumns(sideData.optJSONArray("rooms"));
            JSONArray bookings = sideData.optJSONArray("bookings");
            if (bookings == null || bookings.length() == 0) {
                Log.i(TAG, "No side meeting bookings");
                return batch;
            }

            for (int i = 0; i < bookings.length(); i++) {
                JSONObject booking = bookings.optJSONObject(i);
                if (booking == null) continue;
                ContentProviderOperation[] ops = buildBookingOps(booking, roomSubColumn, versionBuild, resolver);
                if (ops != null) {
                    Collections.addAll(batch, ops);
                }
            }
            Log.i(TAG, "Prepared " + batch.size() + " ops for " + bookings.length() + " side bookings");
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse side meetings data", e);
            batch.clear();
        }
        return batch;
    }

    private static HashMap<Long, Integer> assignRoomSubColumns(JSONArray rooms) {
        HashMap<Long, Integer> map = new HashMap<>();
        if (rooms == null || rooms.length() == 0) {
            return map;
        }
        ArrayList<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < rooms.length(); i++) {
            JSONObject room = rooms.optJSONObject(i);
            if (room != null) sorted.add(room);
        }
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                String ta = a.optString("title", a.optString("slug", ""));
                String tb = b.optString("title", b.optString("slug", ""));
                return ta.compareToIgnoreCase(tb);
            }
        });
        for (int i = 0; i < sorted.size(); i++) {
            long id = sorted.get(i).optLong("id", -1);
            if (id >= 0) {
                map.put(id, Math.min(i, 1)); // at most two parallel rooms
            }
        }
        return map;
    }

    private static ContentProviderOperation[] buildBookingOps(
            JSONObject booking, HashMap<Long, Integer> roomSubColumn,
            long versionBuild, ContentResolver resolver) {
        try {
            long bookingId = booking.optLong("id", -1);
            if (bookingId < 0) return null;

            String title = booking.optString("title", "").trim();
            if (title.isEmpty()) return null;

            String startIso = booking.optString("start", "");
            String endIso = booking.optString("end", "");
            long startMillis = parseIsoMillis(startIso);
            long endMillis = parseIsoMillis(endIso);
            if (startMillis <= 0 || endMillis <= startMillis) {
                Log.w(TAG, "Bad times for booking " + bookingId);
                return null;
            }

            String roomName = booking.optString("roomName", "").trim();
            long roomApiId = booking.optLong("roomId", -1);
            int sub = roomSubColumn.containsKey(roomApiId) ? roomSubColumn.get(roomApiId) : 0;
            String blockType = ParserUtils.BLOCK_TYPE_SIDE_MEETING + sub;

            String sessionKey = "side-" + bookingId;
            String sessionId = Sessions.generateSessionId(sessionKey);
            // Unique block per booking (1:1) so parallel same-slot rooms do not collide.
            String blockId = sessionId;
            String roomId = roomName.isEmpty() ? null : Rooms.generateRoomId(roomName);

            String joinUrl = booking.optString("location", "").trim();
            String description = booking.optString("description", "").trim();
            String organizer = booking.optString("organizerName", "").trim();
            String organizerEmail = booking.optString("organizerEmail", "").trim();
            String areas = joinAreas(booking.optJSONArray("areas"));

            StringBuilder abstractText = new StringBuilder();
            if (!organizer.isEmpty()) {
                abstractText.append("Organizer: ").append(organizer);
                if (!organizerEmail.isEmpty()) {
                    abstractText.append(" <").append(organizerEmail).append(">");
                }
                abstractText.append("\n\n");
            }
            if (!areas.isEmpty()) {
                abstractText.append("Areas: ").append(areas).append("\n\n");
            }
            abstractText.append(description);

            ArrayList<ContentProviderOperation> ops = new ArrayList<>();

            if (roomId != null) {
                ops.add(ContentProviderOperation.newInsert(Rooms.CONTENT_URI)
                        .withValue(Rooms.ROOM_ID, roomId)
                        .withValue(Rooms.ROOM_NAME, roomName)
                        .withValue(Rooms.ROOM_FLOOR, " ")
                        .build());
            }

            ops.add(ContentProviderOperation.newInsert(Blocks.CONTENT_URI)
                    .withValue(Blocks.UPDATED, versionBuild)
                    .withValue(Blocks.BLOCK_ID, blockId)
                    .withValue(Blocks.BLOCK_TITLE, title)
                    .withValue(Blocks.BLOCK_START, startMillis)
                    .withValue(Blocks.BLOCK_END, endMillis)
                    .withValue(Blocks.BLOCK_TYPE, blockType)
                    .build());

            ContentProviderOperation.Builder session = ContentProviderOperation.newInsert(Sessions.CONTENT_URI)
                    .withValue(Sessions.UPDATED, versionBuild)
                    .withValue(Sessions.SESSION_ID, sessionId)
                    .withValue(Sessions.SESSION_TITLE, title)
                    .withValue(Sessions.SESSION_ABSTRACT, abstractText.toString().trim())
                    .withValue(Sessions.SESSION_URL, joinUrl)
                    .withValue(Sessions.SESSION_KEYWORDS, areas)
                    .withValue(Sessions.SESSION_REQUIREMENTS, null)
                    .withValue(Sessions.BLOCK_ID, blockId)
                    .withValue(Sessions.ROOM_ID, roomId)
                    .withValue(Sessions.SESSION_PDF_URL, null)
                    .withValue(Sessions.SESSION_DRAFTS_URL, null)
                    .withValue(Sessions.SESSION_RES_URI, null)
                    .withValue(Sessions.SESSION_IS_BOF, 0);

            final Uri sessionUri = Sessions.buildSessionUri(sessionId);
            final int starred = querySessionStarred(sessionUri, resolver);
            if (starred != -1) {
                session.withValue(Sessions.SESSION_STARRED, starred);
            }
            ops.add(session.build());

            return ops.toArray(new ContentProviderOperation[0]);
        } catch (Exception e) {
            Log.w(TAG, "Skipping booking", e);
            return null;
        }
    }

    private static String joinAreas(JSONArray areas) {
        if (areas == null || areas.length() == 0) return "";
        ArrayList<String> parts = new ArrayList<>();
        for (int i = 0; i < areas.length(); i++) {
            String a = areas.optString(i, "").trim();
            if (!a.isEmpty()) parts.add(a);
        }
        return TextUtils.join(", ", parts);
    }

    private static long parseIsoMillis(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            // Fallback: strip fractional seconds
            try {
                String cleaned = iso.replaceAll("\\.\\d+", "");
                return Instant.parse(cleaned).toEpochMilli();
            } catch (Exception e2) {
                Log.w(TAG, "Cannot parse ISO time: " + iso);
                return 0;
            }
        }
    }

    private static int querySessionStarred(Uri uri, ContentResolver resolver) {
        Cursor cursor = resolver.query(uri, new String[]{Sessions.SESSION_STARRED}, null, null, null);
        int starred = -1;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    starred = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return starred;
    }
}
