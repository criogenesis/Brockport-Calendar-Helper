package com.amazon.ask.calendarhelper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import me.xdrop.fuzzywuzzy.FuzzySearch;


public class BrockportCalendar {
    private static final String WEBSITE = "https://www.brockport.edu/academics/calendar/";

    private static final double DATE_SIMILARITY_THRESHOLD = 0.20;
    private final HashMap<String, Date> CALENDAR = new HashMap<>();
    private List<DateInfo> dates = new ArrayList<>();
    private static final int MAX_DATES = 3;

    /**
     * Initializes a connection with the Brockport calendar website, and retrieves and stores all dates and events.
     *
     * @throws IOException If the website connection cannot be succesfully established.
     */
    public BrockportCalendar(String semester) throws IOException {
        Document doc = Jsoup.connect(WEBSITE).get();
        Elements events = doc.getElementsByClass("ev");
        Elements dates = doc.getElementsByClass("date");

        // Since some dates have multiple events, create multiple key-value pairs with the same date, appending "Day X"
        // to the name, with X being the Xth occurrence of that event.

        for(int x = 0; x < dates.size(); x++) {
            ArrayList<Date> dateList = formatDate(dates.get(x).text());

            for (Date date : dateList) {
                String event = events.get(x).text();

                if (CALENDAR.containsKey(event)) {
                    List<String> eventSplit = new ArrayList<>(Arrays.asList(event.split(" ")));

                    if (eventSplit.get(eventSplit.size() - 2).equals("Day")) {
                        eventSplit.set(eventSplit.size() - 1,
                                String.valueOf(Integer.parseInt(eventSplit.get(eventSplit.size() - 1)) + 1));
                    } else {
                        eventSplit.add("Day");
                        eventSplit.add("2");
                    }

                    event = StringUtils.join(eventSplit, " ");
                }
                if(semester.equals("spring"))
                {
                    if(isInSpring(date))
                    {
                        CALENDAR.put(event, date);
                    }
                }
                if(semester.equals("fall"))
                {
                    if(isInFall(date))
                    {
                        CALENDAR.put(event, date);
                    }
                }

            }
        }
    }

    /**
     * Formats a string of all date and time variants that the Brockport calendar uses into a format parsable by
     * {@link Date}. If a date range is detected, multiple strings will be formatted.
     *
     * The following are examples of formats that are understood:
     * July 4, 2020
     * August 23, 2019, Friday
     * September 26 – 28, 2019
     * October 14 & 15, 2019, Monday & Tuesday
     * August 26, 2019, Monday, 8 AM
     * April 10, 2020, Friday, 9 AM – 5 PM
     *
     * @param dateString A date and time string in one of the expected input formats.
     * @return A list of parsed dates.
     * @throws InputMismatchException If the given date and time string is not in a recognized
     */
    private ArrayList<Date> formatDate(String dateString) throws InputMismatchException {
        List<String> dateSplit = Arrays.asList(dateString.split(" "));
        SimpleDateFormat dateFormat;
        ArrayList<Date> dates = new ArrayList<>();

        try {
            switch (dateSplit.size()) {
                case 3:
                    // e.g. July 4, 2020
                    dateFormat = new SimpleDateFormat("MMMMM d, yyyy");
                    dates.add(dateFormat.parse(dateString));
                    break;
                case 4:
                    // e.g. August 23, 2019, Friday
                    dateFormat = new SimpleDateFormat("MMMMM d, yyyy, EEEEE");
                    dates.add(dateFormat.parse(dateString));
                    break;
                case 5:
                    // e.g. September 26 – 28, 2019
                case 8:
                    // e.g. October 14 & 15, 2019, Monday & Tuesday
                    dateFormat = new SimpleDateFormat("MMMMM d yyyy");
                    Date startDate = dateFormat.parse(String.format("%s %s %s", dateSplit.get(0),
                                                                                dateSplit.get(1),
                                                                                dateSplit.get(4)));

                    dateFormat = new SimpleDateFormat("MMMMM d, yyyy");
                    Date endDate = dateFormat.parse(String.format("%s %s %s", dateSplit.get(0),
                                                                              dateSplit.get(3),
                                                                              dateSplit.get(4)));

                    while (!startDate.after(endDate)) {
                        dates.add(startDate);
                        startDate = DateUtils.addDays(startDate, 1);
                    }

                    break;
                case 6:
                    // e.g. August 26, 2019, Monday, 8 AM
                    dateFormat = new SimpleDateFormat("MMMMM d, yyyy, EEEEE, hh a");
                    dates.add(dateFormat.parse(dateString));
                case 9:
                    // e.g. April 10, 2020, Friday, 9 AM – 5 PM
                    // Current method: chop off the second time and run it through like normal.
                    dateSplit = dateSplit.subList(0, 6);
                    dateString = String.join(" ", dateSplit);
                    break;
                default:
                    throw new InputMismatchException("Input " + dateString + " not in expected format.");
            }
        } catch (ParseException e) {
        } catch (InputMismatchException e) {
            throw new InputMismatchException("Input " + dateString + " not in expected format.");
        }

        return dates;
    }

    /**
     * Retrieves the {@link Date} for an event. Since multiple events with the same name can occur,
     *
     * @param eventName The event name.
     * @return The {@link Date} for an event.
     *         null if no event is found.
     */
    public List<DateInfo> getEventDates(String eventName) {
        // Remove all non-alphanumeric characters from the event name.
        eventName = eventName.toLowerCase().replaceAll("[^a-z0-9]", "");

//        Date eventDate = null;
//        double eventSimilarity = 0.0;

        // Iterate through every key-value pair and compare the event name similarity to the event in the current loop
        // state.
        for (Map.Entry<String, Date> entry : CALENDAR.entrySet()) {
            // Remove all non-alphanumeric characters from the current event.
            String tempEvent = entry.getKey().toLowerCase().replaceAll("[^a-z0-9]", "");
            Date tempDate = entry.getValue();
//            if (tempEvent.contains(eventName)) {
//                eventDate = tempDate;
//                eventSimilarity = 1.0;
//            } else {
//                double tempSimilarity = similarity(eventName, tempEvent);
//
//                if (tempSimilarity >= 0.20 && tempSimilarity > eventSimilarity) {
//                    eventDate = tempDate;
//                    eventSimilarity = tempSimilarity;
//                }
//            }
            insertDate(new DateInfo(getEventName(tempDate),
                    tempDate,
                    tempEvent.contains(eventName) ? 100 : FuzzySearch.partialRatio(eventName, tempEvent)));
        }

        return dates;
    }

    /**
     * Retrieves the event name for a given {@link Date}, considering only the date.
     *
     * @param eventDate The {@link Date} to consider.
     * @return The name of the event.
     *         null if no event is found.
     */
    public String getEventName(Date eventDate) {
        // Iterate through every key-value pair and compare the current date to eventDate. If they are the same date,
        // return it.
        for (Map.Entry<String, Date> entry : CALENDAR.entrySet()) {
            if (DateUtils.isSameDay(entry.getValue(), eventDate)) {
                return entry.getKey();
            }
        }

        return null;
    }
//    public List<DateInfo> getSpring(String semester) {
//        // Iterate through every key-value pair and compare the current date to eventDate. If they are the same date,
//        // return it.
//        List<DateInfo> springDates = new ArrayList<>();
//        for (Map.Entry<String, Date> entry : CALENDAR.entrySet()) {
//            System.out.println(entry.getValue());
//            System.out.println((entry.getValue().getClass()));
//            System.out.println(isInSpring(entry.getValue()));
//            if(isInSpring(entry.getValue()))
//            {
//
//                springDates.add()
//            }
//        }
//
//        return null;
//    }
    boolean isInSpring(Date testDate) {
        int theMonth = testDate.getMonth();
        if(theMonth >= 1 && theMonth <= 5)
        {
            return true;
        }
        return false;
    }
    boolean isInFall(Date testDate) {
        int theMonth = testDate.getMonth();
        if(theMonth >= 8 && theMonth <= 12)
        {
            return true;
        }
        return false;
    }
    public int getDaysUntilEvent(String event) {
        LocalDate eventDate = getEventDates(event).get(dates.size() - 1)
                .getDate()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return (int) LocalDate.now().until(eventDate, ChronoUnit.DAYS);
    }

    private void insertDate(DateInfo dateInfo) {
        dates.sort(Comparator.comparing(o -> ((DateInfo) o).getSimilarity()).reversed());

        if (dateInfo.getSimilarity() >= DATE_SIMILARITY_THRESHOLD) {
            if (dates.size() < MAX_DATES) {
                dates.add(dateInfo);
            } else {
                int lastIndex = dates.size() - 1;

                if (dateInfo.getSimilarity().compareTo(dates.get(lastIndex).getSimilarity()) >= 0) {
                    dates.set(lastIndex, dateInfo);
                }
            }
        }

        dates.sort(Comparator.comparing(o -> ((DateInfo) o).getSimilarity()).reversed());
    }
    /**
     * Calculates the similarity between the two given {@link String}s.
     *
     * @param s1 The first string to compare.
     * @param s2 The second string to compare.
     * @return The calculated similarity.
     */
//    public double similarity(String s1, String s2) {
//        String longer = s1;
//        String shorter = s2;
//
//        // Longer should always have the greater length.
//        if (s1.length() < s2.length()) {
//            longer = s2;
//            shorter = s1;
//        }
//
//        int longerLength = longer.length();
//
//        // If both strings are of zero length.
//        if (longerLength == 0) {
//            return 1.0;
//        }
//
//        return (longerLength - StringUtils.getLevenshteinDistance(longer, shorter)) / (double) longerLength;
//    }
}
