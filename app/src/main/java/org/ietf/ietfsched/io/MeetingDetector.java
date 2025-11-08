package org.ietf.ietfsched.io;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Detects the current or upcoming IETF meeting by querying the Datatracker API.
 */
public class MeetingDetector {
    private static final String TAG = "MeetingDetector";
    private static final boolean DEBUG = true;
    
    private static final String MEETINGS_API_URL = "https://datatracker.ietf.org/api/v1/meeting/meeting/";
    
    // Cache settings
    private static final long CACHE_DURATION_DURING_MEETING = 60 * 60 * 1000; // 1 hour
    private static final long CACHE_DURATION_BETWEEN_MEETINGS = 24 * 60 * 60 * 1000; // 1 day
    private static final long CACHE_JITTER_MAX = 5 * 60 * 1000; // 5 minutes randomization
    
    private static MeetingMetadata sCachedMeeting = null;
    private static long sCacheTimestamp = 0;
    
    private final RemoteExecutor remoteExecutor;
    private final Random random = new Random();
    
    public MeetingDetector(RemoteExecutor executor) {
        this.remoteExecutor = executor;
    }
    
    /**
     * Detects the current or next upcoming IETF meeting.
     * Returns cached result if still valid, otherwise fetches from API.
     * 
     * @return MeetingMetadata for the current/upcoming meeting, or null if detection failed
     */
    public MeetingMetadata detectCurrentMeeting() {
        long now = System.currentTimeMillis();
        
        // Check cache validity
        if (sCachedMeeting != null && isCacheValid(now)) {
            if (DEBUG) Log.d(TAG, "Using cached meeting: IETF " + sCachedMeeting.number);
            return sCachedMeeting;
        }
        
        // Fetch fresh meeting list
        List<MeetingMetadata> meetings = fetchMeetingList();
        if (meetings == null || meetings.isEmpty()) {
            Log.e(TAG, "No meetings found in API");
            return sCachedMeeting; // Return stale cache if available
        }
        
        // Sort by start date (newest first)
        Collections.sort(meetings, new Comparator<MeetingMetadata>() {
            @Override
            public int compare(MeetingMetadata a, MeetingMetadata b) {
                return Long.compare(b.startMillis, a.startMillis);
            }
        });
        
        // Find current or next meeting
        MeetingMetadata selectedMeeting = selectMeeting(meetings, now);
        
        if (selectedMeeting != null) {
            sCachedMeeting = selectedMeeting;
            sCacheTimestamp = now;
            if (DEBUG) Log.d(TAG, "Selected meeting: IETF " + selectedMeeting.number + 
                " (" + selectedMeeting.city + "), agenda=" + selectedMeeting.agendaAvailable);
        }
        
        return selectedMeeting;
    }
    
    /**
     * Fetches the list of IETF meetings from the Datatracker API.
     */
    private List<MeetingMetadata> fetchMeetingList() {
        try {
            String jsonStr = remoteExecutor.executeGet(MEETINGS_API_URL);
            if (jsonStr == null) {
                Log.e(TAG, "Failed to fetch meetings list");
                return null;
            }
            
            JSONObject root = new JSONObject(jsonStr);
            JSONArray objects = root.optJSONArray("objects");
            
            if (objects == null) {
                Log.e(TAG, "No 'objects' array in API response");
                return null;
            }
            
            List<MeetingMetadata> meetings = new ArrayList<>();
            
            for (int i = 0; i < objects.length(); i++) {
                try {
                    JSONObject meetingJson = objects.getJSONObject(i);
                    
                    // Filter for IETF meetings only
                    String type = meetingJson.optString("type", "");
                    if (!type.contains("ietf")) {
                        if (DEBUG) Log.d(TAG, "Skipping non-IETF meeting: " + 
                            meetingJson.optString("number"));
                        continue;
                    }
                    
                    MeetingMetadata meeting = MeetingMetadata.fromJSON(meetingJson);
                    
                    // Check if agenda is available
                    boolean agendaAvailable = checkAgendaAvailable(meeting.agendaUrl);
                    meeting = meeting.withAgendaAvailability(agendaAvailable);
                    
                    meetings.add(meeting);
                    
                    if (DEBUG) Log.d(TAG, "Found meeting: IETF " + meeting.number + 
                        " (" + meeting.city + "), start=" + meeting.startMillis + 
                        ", agenda=" + agendaAvailable);
                        
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse meeting JSON", e);
                }
            }
            
            if (DEBUG) Log.d(TAG, "Fetched " + meetings.size() + " IETF meetings");
            return meetings;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching meetings list", e);
            return null;
        }
    }
    
    /**
     * Checks if the agenda JSON is available for a meeting.
     */
    private boolean checkAgendaAvailable(String agendaUrl) {
        try {
            String jsonStr = remoteExecutor.executeGet(agendaUrl);
            
            if (jsonStr == null || jsonStr.trim().isEmpty()) {
                if (DEBUG) Log.d(TAG, "Agenda at " + agendaUrl + ": empty response");
                return false;
            }
            
            JSONObject agenda = new JSONObject(jsonStr);
            
            if (agenda.length() == 0) {
                if (DEBUG) Log.d(TAG, "Agenda at " + agendaUrl + ": empty JSON object");
                return false;
            }
            
            String firstKey = agenda.keys().next();
            if (firstKey == null) {
                if (DEBUG) Log.d(TAG, "Agenda at " + agendaUrl + ": no keys in JSON");
                return false;
            }
            
            Object value = agenda.opt(firstKey);
            if (!(value instanceof JSONArray)) {
                if (DEBUG) Log.d(TAG, "Agenda at " + agendaUrl + ": first key is not an array");
                return false;
            }
            
            JSONArray meetings = (JSONArray) value;
            if (meetings.length() == 0) {
                if (DEBUG) Log.d(TAG, "Agenda at " + agendaUrl + ": meetings array is empty");
                return false;
            }
            
            if (DEBUG) Log.d(TAG, "Agenda available at " + agendaUrl + " (valid JSON with " + meetings.length() + " meetings)");
            return true;
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "Agenda check failed for " + agendaUrl + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Selects the most appropriate meeting from the list.
     * Priority:
     * 1. Currently ongoing meeting with agenda
     * 2. Upcoming meeting with agenda
     * 3. Previous meeting with agenda (fallback)
     */
    private MeetingMetadata selectMeeting(List<MeetingMetadata> meetings, long now) {
        MeetingMetadata currentMeeting = null;
        MeetingMetadata upcomingMeeting = null;
        MeetingMetadata previousMeeting = null;
        
        for (MeetingMetadata meeting : meetings) {
            boolean isOngoing = now >= meeting.startMillis && now <= meeting.endMillis;
            boolean isUpcoming = now < meeting.startMillis;
            boolean isPrevious = now > meeting.endMillis;
            
            if (isOngoing && meeting.agendaAvailable) {
                currentMeeting = meeting;
                break; // Prefer current meeting
            } else if (isUpcoming && meeting.agendaAvailable) {
                if (upcomingMeeting == null || meeting.startMillis < upcomingMeeting.startMillis) {
                    upcomingMeeting = meeting; // Get the nearest upcoming
                }
            } else if (isPrevious) {
                // For past meetings, don't check agendaAvailable - they should always have it
                if (previousMeeting == null || meeting.startMillis > previousMeeting.startMillis) {
                    previousMeeting = meeting; // Get the most recent previous
                }
            }
        }
        
        // Return in priority order
        if (currentMeeting != null) {
            if (DEBUG) Log.d(TAG, "Selected current meeting: IETF " + currentMeeting.number);
            return currentMeeting;
        } else if (upcomingMeeting != null) {
            if (DEBUG) Log.d(TAG, "Selected upcoming meeting: IETF " + upcomingMeeting.number);
            return upcomingMeeting;
        } else if (previousMeeting != null) {
            if (DEBUG) Log.d(TAG, "Using previous meeting as fallback: IETF " + previousMeeting.number);
            return previousMeeting;
        }
        
        Log.e(TAG, "No suitable meeting found");
        return null;
    }
    
    /**
     * Checks if the cached meeting is still valid.
     */
    private boolean isCacheValid(long now) {
        if (sCachedMeeting == null || sCacheTimestamp == 0) {
            return false;
        }
        
        long age = now - sCacheTimestamp;
        long jitter = random.nextInt((int) CACHE_JITTER_MAX);
        
        // During meeting: refresh hourly (with jitter)
        boolean isDuringMeeting = now >= sCachedMeeting.startMillis && now <= sCachedMeeting.endMillis;
        if (isDuringMeeting) {
            return age < (CACHE_DURATION_DURING_MEETING + jitter);
        }
        
        // Between meetings: refresh daily (with jitter)
        return age < (CACHE_DURATION_BETWEEN_MEETINGS + jitter);
    }
}
