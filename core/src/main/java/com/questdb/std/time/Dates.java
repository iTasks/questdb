/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2017 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.std.time;

import com.questdb.ex.NumericException;
import com.questdb.misc.Chars;
import com.questdb.misc.Numbers;
import com.questdb.std.LongList;
import com.questdb.std.str.CharSink;
import com.questdb.txt.sink.StringSink;

final public class Dates {

    public static final long DAY_MILLIS = 86400000L;
    public static final long HOUR_MILLIS = 3600000L;
    public static final long MINUTE_MILLIS = 60000;
    public static final long SECOND_MILLIS = 1000;
    public static final int STATE_INIT = 0;
    public static final int STATE_UTC = 1;
    public static final int STATE_GMT = 2;
    public static final int STATE_HOUR = 3;
    public static final int STATE_DELIM = 4;
    public static final int STATE_MINUTE = 5;
    public static final int STATE_END = 6;
    public static final int STATE_SIGN = 7;
    private static final long AVG_YEAR_MILLIS = (long) (365.2425 * DAY_MILLIS);
    private static final long YEAR_MILLIS = 365 * DAY_MILLIS;
    private static final long LEAP_YEAR_MILLIS = 366 * DAY_MILLIS;
    private static final long HALF_YEAR_MILLIS = AVG_YEAR_MILLIS / 2;
    private static final long EPOCH_MILLIS = 1970L * AVG_YEAR_MILLIS;
    private static final long HALF_EPOCH_MILLIS = EPOCH_MILLIS / 2;
    private static final int DAY_HOURS = 24;
    private static final int HOUR_MINUTES = 60;
    private static final int MINUTE_SECONDS = 60;
    private static final int DAYS_0000_TO_1970 = 719527;
    private static final int[] DAYS_PER_MONTH = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };
    private static final String[] DAYS_OF_WEEK = {
            "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    };
    private static final long[] MIN_MONTH_OF_YEAR_MILLIS = new long[12];
    private static final long[] MAX_MONTH_OF_YEAR_MILLIS = new long[12];
    private static final String[] MONTHS_OF_YEAR = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    private Dates() {
    }

    public static long addDays(long millis, int days) {
        return millis + days * DAY_MILLIS;
    }

    public static long addHours(long millis, int hours) {
        return millis + hours * HOUR_MILLIS;
    }

    public static long addMonths(final long millis, int months) {
        if (months == 0) {
            return millis;
        }
        int y = Dates.getYear(millis);
        boolean l = Dates.isLeapYear(y);
        int m = getMonthOfYear(millis, y, l);
        int _y;
        int _m = m - 1 + months;
        if (_m > -1) {
            _y = y + _m / 12;
            _m = (_m % 12) + 1;
        } else {
            _y = y + _m / 12 - 1;
            _m = -_m % 12;
            if (_m == 0) {
                _m = 12;
            }
            _m = 12 - _m + 1;
            if (_m == 1) {
                _y += 1;
            }
        }
        int _d = getDayOfMonth(millis, y, m, l);
        int maxDay = getDaysPerMonth(_m, isLeapYear(_y));
        if (_d > maxDay) {
            _d = maxDay;
        }
        return toMillis(_y, _m, _d) + getTime(millis) + (millis < 0 ? 1 : 0);
    }

    public static long addPeriod(long lo, char type, int period) throws NumericException {
        switch (type) {
            case 's':
                return lo + period * Dates.SECOND_MILLIS;
            case 'm':
                return lo + period * Dates.MINUTE_MILLIS;
            case 'h':
                return Dates.addHours(lo, period);
            case 'd':
                return Dates.addDays(lo, period);
            case 'M':
                return Dates.addMonths(lo, period);
            case 'y':
                return Dates.addYear(lo, period);
            default:
                throw NumericException.INSTANCE;
        }
    }

    public static long addYear(long millis, int years) {
        if (years == 0) {
            return millis;
        }

        int y, m;
        boolean l;

        return yearMillis((y = getYear(millis)) + years, l = isLeapYear(y + years))
                + monthOfYearMillis(m = getMonthOfYear(millis, y, l), l)
                + (getDayOfMonth(millis, y, m, l) - 1) * DAY_MILLIS
                + getTime(millis)
                + (millis < 0 ? 1 : 0);

    }

    public static void append0(CharSink sink, int val) {
        if (Math.abs(val) < 10) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    public static void append00(CharSink sink, int val) {
        int v = Math.abs(val);
        if (v < 10) {
            sink.put('0').put('0');
        } else if (v < 100) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    public static void append000(CharSink sink, int val) {
        int v = Math.abs(val);
        if (v < 10) {
            sink.put('0').put('0').put('0');
        } else if (v < 100) {
            sink.put('0').put('0');
        } else if (v < 1000) {
            sink.put('0');
        }
        Numbers.append(sink, val);
    }

    public static long ceilDD(long millis) {
        int y, m;
        boolean l;
        return yearMillis(y = getYear(millis), l = isLeapYear(y))
                + monthOfYearMillis(m = getMonthOfYear(millis, y, l), l)
                + (getDayOfMonth(millis, y, m, l) - 1) * DAY_MILLIS
                + 23 * HOUR_MILLIS
                + 59 * MINUTE_MILLIS
                + 59 * SECOND_MILLIS
                + 999L
                ;
    }

    public static long ceilMM(long millis) {
        int y, m;
        boolean l;
        return yearMillis(y = getYear(millis), l = isLeapYear(y))
                + monthOfYearMillis(m = getMonthOfYear(millis, y, l), l)
                + (getDaysPerMonth(m, l) - 1) * DAY_MILLIS
                + 23 * HOUR_MILLIS
                + 59 * MINUTE_MILLIS
                + 59 * SECOND_MILLIS
                + 999L
                ;
    }

    public static long ceilYYYY(long millis) {
        int y;
        boolean l;
        return yearMillis(y = getYear(millis), l = isLeapYear(y))
                + monthOfYearMillis(12, l)
                + (DAYS_PER_MONTH[11] - 1) * DAY_MILLIS
                + 23 * HOUR_MILLIS
                + 59 * MINUTE_MILLIS
                + 59 * SECOND_MILLIS
                + 999L;
    }

    public static long endOfYear(int year) {
        return toMillis(year, 12, 31, 23, 59) + 59 * 1000L + 999L;
    }

    public static long floorDD(long millis) {
        return millis - getTime(millis);
    }

    public static long floorHH(long millis) {
        return millis - millis % HOUR_MILLIS;
    }

    public static long floorMI(long millis) {
        return millis - millis % MINUTE_MILLIS;
    }

    public static long floorMM(long millis) {
        int y;
        boolean l;
        return yearMillis(y = getYear(millis), l = isLeapYear(y)) + monthOfYearMillis(getMonthOfYear(millis, y, l), l);
    }

    public static long floorYYYY(long millis) {
        int y;
        return yearMillis(y = getYear(millis), isLeapYear(y));
    }

    public static int getDayOfMonth(long millis, int year, int month, boolean leap) {
        long dateMillis = yearMillis(year, leap);
        dateMillis += monthOfYearMillis(month, leap);
        return (int) ((millis - dateMillis) / DAY_MILLIS) + 1;
    }

    public static int getDayOfWeek(long millis) {
        // 1970-01-01 is Thursday.
        long d;
        if (millis >= 0) {
            d = millis / DAY_MILLIS;
        } else {
            d = (millis - (DAY_MILLIS - 1)) / DAY_MILLIS;
            if (d < -3) {
                return 7 + (int) ((d + 4) % 7);
            }
        }
        return 1 + (int) ((d + 3) % 7);
    }

    public static int getDayOfWeekSundayFirst(long millis) {
        // 1970-01-01 is Thursday.
        long d;
        if (millis >= 0) {
            d = millis / DAY_MILLIS;
        } else {
            d = (millis - (DAY_MILLIS - 1)) / DAY_MILLIS;
            if (d < -4) {
                return 7 + (int) ((d + 5) % 7);
            }
        }
        return 1 + (int) ((d + 4) % 7);
    }

    /**
     * Days in a given month. This method expects you to know if month is in leap year.
     *
     * @param m    month from 1 to 12
     * @param leap true if this is for leap year
     * @return number of days in month.
     */
    public static int getDaysPerMonth(int m, boolean leap) {
        return leap & m == 2 ? 29 : DAYS_PER_MONTH[m - 1];
    }

    public static int getHourOfDay(long millis) {
        if (millis > -1) {
            return (int) ((millis / HOUR_MILLIS) % DAY_HOURS);
        } else {
            return DAY_HOURS - 1 + (int) (((millis + 1) / HOUR_MILLIS) % DAY_HOURS);
        }
    }

    public static int getMillisOfSecond(long millis) {
        if (millis > -1) {
            return (int) (millis % 1000);
        } else {
            return 1000 - 1 + (int) ((millis + 1) % 1000);
        }
    }

    public static int getMinuteOfHour(long millis) {
        if (millis > -1) {
            return (int) ((millis / MINUTE_MILLIS) % HOUR_MINUTES);
        } else {
            return HOUR_MINUTES - 1 + (int) (((millis + 1) / MINUTE_MILLIS) % HOUR_MINUTES);
        }
    }

    /**
     * Calculates month of year from absolute millis.
     *
     * @param millis millis since 1970
     * @param year   year of month
     * @param leap   true if year was leap
     * @return month of year
     */
    public static int getMonthOfYear(long millis, int year, boolean leap) {
        int i = (int) ((millis - yearMillis(year, leap)) >> 10);
        return leap
                ? ((i < 182 * 84375)
                ? ((i < 91 * 84375)
                ? ((i < 31 * 84375) ? 1 : (i < 60 * 84375) ? 2 : 3)
                : ((i < 121 * 84375) ? 4 : (i < 152 * 84375) ? 5 : 6))
                : ((i < 274 * 84375)
                ? ((i < 213 * 84375) ? 7 : (i < 244 * 84375) ? 8 : 9)
                : ((i < 305 * 84375) ? 10 : (i < 335 * 84375) ? 11 : 12)))
                : ((i < 181 * 84375)
                ? ((i < 90 * 84375)
                ? ((i < 31 * 84375) ? 1 : (i < 59 * 84375) ? 2 : 3)
                : ((i < 120 * 84375) ? 4 : (i < 151 * 84375) ? 5 : 6))
                : ((i < 273 * 84375)
                ? ((i < 212 * 84375) ? 7 : (i < 243 * 84375) ? 8 : 9)
                : ((i < 304 * 84375) ? 10 : (i < 334 * 84375) ? 11 : 12)));
    }

    public static int getSecondOfMinute(long millis) {
        if (millis > -1) {
            return (int) ((millis / SECOND_MILLIS) % MINUTE_SECONDS);
        } else {
            return MINUTE_SECONDS - 1 + (int) (((millis + 1) / SECOND_MILLIS) % MINUTE_SECONDS);
        }
    }

    /**
     * Calculates year number from millis.
     *
     * @param millis time millis.
     * @return year
     */
    public static int getYear(long millis) {
        long mid = (millis >> 1) + HALF_EPOCH_MILLIS;
        if (mid < 0) {
            mid = mid - HALF_YEAR_MILLIS + 1;
        }
        int year = (int) (mid / HALF_YEAR_MILLIS);

        boolean leap = isLeapYear(year);
        long yearStart = yearMillis(year, leap);
        long diff = millis - yearStart;

        if (diff < 0) {
            year--;
        } else if (diff >= YEAR_MILLIS) {
            yearStart += leap ? LEAP_YEAR_MILLIS : YEAR_MILLIS;
            if (yearStart <= millis) {
                year++;
            }
        }

        return year;
    }

    /**
     * Calculates if year is leap year using following algorithm:
     * <p>
     * http://en.wikipedia.org/wiki/Leap_year
     *
     * @param year the year
     * @return true if year is leap
     */
    public static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }

    public static long monthOfYearMillis(int month, boolean leap) {
        return leap ? MAX_MONTH_OF_YEAR_MILLIS[month - 1] : MIN_MONTH_OF_YEAR_MILLIS[month - 1];
    }

    public static long nextOrSameDayOfWeek(long millis, int dow) {
        int thisDow = getDayOfWeek(millis);
        if (thisDow == dow) {
            return millis;
        }

        if (thisDow < dow) {
            return millis + (dow - thisDow) * DAY_MILLIS;
        } else {
            return millis + (7 - (thisDow - dow)) * DAY_MILLIS;
        }
    }

    public static Interval parseInterval(CharSequence seq) throws NumericException {
        int lim = seq.length();
        int p = 0;
        if (lim < 4) {
            throw NumericException.INSTANCE;
        }
        int year = Numbers.parseInt(seq, p, p += 4);
        boolean l = isLeapYear(year);
        if (checkLen(p, lim)) {
            checkChar(seq, p++, lim, '-');
            int month = Numbers.parseInt(seq, p, p += 2);
            checkRange(month, 1, 12);
            if (checkLen(p, lim)) {
                checkChar(seq, p++, lim, '-');
                int day = Numbers.parseInt(seq, p, p += 2);
                checkRange(day, 1, getDaysPerMonth(month, l));
                if (checkLen(p, lim)) {
                    checkChar(seq, p++, lim, 'T');
                    int hour = Numbers.parseInt(seq, p, p += 2);
                    checkRange(hour, 0, 23);
                    if (checkLen(p, lim)) {
                        checkChar(seq, p++, lim, ':');
                        int min = Numbers.parseInt(seq, p, p += 2);
                        checkRange(min, 0, 59);
                        if (checkLen(p, lim)) {
                            checkChar(seq, p++, lim, ':');
                            int sec = Numbers.parseInt(seq, p, p += 2);
                            checkRange(sec, 0, 59);
                            if (p < lim) {
                                throw NumericException.INSTANCE;
                            } else {
                                // seconds
                                return new Interval(yearMillis(year, l)
                                        + monthOfYearMillis(month, l)
                                        + (day - 1) * DAY_MILLIS
                                        + hour * HOUR_MILLIS
                                        + min * MINUTE_MILLIS
                                        + sec * SECOND_MILLIS,
                                        yearMillis(year, l)
                                                + monthOfYearMillis(month, l)
                                                + (day - 1) * DAY_MILLIS
                                                + hour * HOUR_MILLIS
                                                + min * MINUTE_MILLIS
                                                + sec * SECOND_MILLIS
                                                + 999
                                );
                            }
                        } else {
                            // minute
                            return new Interval(yearMillis(year, l)
                                    + monthOfYearMillis(month, l)
                                    + (day - 1) * DAY_MILLIS
                                    + hour * HOUR_MILLIS
                                    + min * MINUTE_MILLIS,
                                    yearMillis(year, l)
                                            + monthOfYearMillis(month, l)
                                            + (day - 1) * DAY_MILLIS
                                            + hour * HOUR_MILLIS
                                            + min * MINUTE_MILLIS
                                            + 59 * SECOND_MILLIS
                                            + 999
                            );
                        }
                    } else {
                        // year + month + day + hour
                        return new Interval(yearMillis(year, l)
                                + monthOfYearMillis(month, l)
                                + (day - 1) * DAY_MILLIS
                                + hour * HOUR_MILLIS,
                                yearMillis(year, l)
                                        + monthOfYearMillis(month, l)
                                        + (day - 1) * DAY_MILLIS
                                        + hour * HOUR_MILLIS
                                        + 59 * MINUTE_MILLIS
                                        + 59 * SECOND_MILLIS
                                        + 999
                        );
                    }
                } else {
                    // year + month + day
                    return new Interval(yearMillis(year, l)
                            + monthOfYearMillis(month, l)
                            + (day - 1) * DAY_MILLIS,
                            yearMillis(year, l)
                                    + monthOfYearMillis(month, l)
                                    + +(day - 1) * DAY_MILLIS
                                    + 23 * HOUR_MILLIS
                                    + 59 * MINUTE_MILLIS
                                    + 59 * SECOND_MILLIS
                                    + 999
                    );
                }
            } else {
                // year + month
                return new Interval(yearMillis(year, l) + monthOfYearMillis(month, l),
                        yearMillis(year, l)
                                + monthOfYearMillis(month, l)
                                + (getDaysPerMonth(month, l) - 1) * DAY_MILLIS
                                + 23 * HOUR_MILLIS
                                + 59 * MINUTE_MILLIS
                                + 59 * SECOND_MILLIS
                                + 999
                );
            }
        } else {
            // year
            return new Interval(yearMillis(year, l) + monthOfYearMillis(1, l),
                    yearMillis(year, l)
                            + monthOfYearMillis(12, l)
                            + (DAYS_PER_MONTH[11] - 1) * DAY_MILLIS
                            + 23 * HOUR_MILLIS
                            + 59 * MINUTE_MILLIS
                            + 59 * SECOND_MILLIS
                            + 999
            );
        }
    }

    public static void parseInterval(CharSequence seq, final int pos, int lim, LongList out) throws NumericException {
        int len = lim - pos;
        int p = pos;
        if (len < 4) {
            throw NumericException.INSTANCE;
        }
        int year = Numbers.parseInt(seq, p, p += 4);
        boolean l = isLeapYear(year);
        if (checkLen(p, len)) {
            checkChar(seq, p++, lim, '-');
            int month = Numbers.parseInt(seq, p, p += 2);
            checkRange(month, 1, 12);
            if (checkLen(p, len)) {
                checkChar(seq, p++, lim, '-');
                int day = Numbers.parseInt(seq, p, p += 2);
                checkRange(day, 1, getDaysPerMonth(month, l));
                if (checkLen(p, len)) {
                    checkChar(seq, p++, lim, 'T');
                    int hour = Numbers.parseInt(seq, p, p += 2);
                    checkRange(hour, 0, 23);
                    if (checkLen(p, len)) {
                        checkChar(seq, p++, lim, ':');
                        int min = Numbers.parseInt(seq, p, p += 2);
                        checkRange(min, 0, 59);
                        if (checkLen(p, len)) {
                            checkChar(seq, p++, lim, ':');
                            int sec = Numbers.parseInt(seq, p, p += 2);
                            checkRange(sec, 0, 59);
                            if (p < len) {
                                throw NumericException.INSTANCE;
                            } else {
                                // seconds
                                out.add(yearMillis(year, l)
                                        + monthOfYearMillis(month, l)
                                        + (day - 1) * DAY_MILLIS
                                        + hour * HOUR_MILLIS
                                        + min * MINUTE_MILLIS
                                        + sec * SECOND_MILLIS);
                                out.add(yearMillis(year, l)
                                        + monthOfYearMillis(month, l)
                                        + (day - 1) * DAY_MILLIS
                                        + hour * HOUR_MILLIS
                                        + min * MINUTE_MILLIS
                                        + sec * SECOND_MILLIS
                                        + 999);
                            }
                        } else {
                            // minute
                            out.add(yearMillis(year, l)
                                    + monthOfYearMillis(month, l)
                                    + (day - 1) * DAY_MILLIS
                                    + hour * HOUR_MILLIS
                                    + min * MINUTE_MILLIS);
                            out.add(yearMillis(year, l)
                                    + monthOfYearMillis(month, l)
                                    + (day - 1) * DAY_MILLIS
                                    + hour * HOUR_MILLIS
                                    + min * MINUTE_MILLIS
                                    + 59 * SECOND_MILLIS
                                    + 999);
                        }
                    } else {
                        // year + month + day + hour
                        out.add(yearMillis(year, l)
                                + monthOfYearMillis(month, l)
                                + (day - 1) * DAY_MILLIS
                                + hour * HOUR_MILLIS);
                        out.add(yearMillis(year, l)
                                + monthOfYearMillis(month, l)
                                + (day - 1) * DAY_MILLIS
                                + hour * HOUR_MILLIS
                                + 59 * MINUTE_MILLIS
                                + 59 * SECOND_MILLIS
                                + 999);
                    }
                } else {
                    // year + month + day
                    out.add(yearMillis(year, l)
                            + monthOfYearMillis(month, l)
                            + (day - 1) * DAY_MILLIS);
                    out.add(yearMillis(year, l)
                            + monthOfYearMillis(month, l)
                            + +(day - 1) * DAY_MILLIS
                            + 23 * HOUR_MILLIS
                            + 59 * MINUTE_MILLIS
                            + 59 * SECOND_MILLIS
                            + 999);
                }
            } else {
                // year + month
                out.add(yearMillis(year, l) + monthOfYearMillis(month, l));
                out.add(yearMillis(year, l)
                        + monthOfYearMillis(month, l)
                        + (DAYS_PER_MONTH[month - 1] - 1) * DAY_MILLIS
                        + 23 * HOUR_MILLIS
                        + 59 * MINUTE_MILLIS
                        + 59 * SECOND_MILLIS
                        + 999);
            }
        } else {
            // year
            out.add(yearMillis(year, l) + monthOfYearMillis(1, l));
            out.add(yearMillis(year, l)
                    + monthOfYearMillis(12, l)
                    + (DAYS_PER_MONTH[11] - 1) * DAY_MILLIS
                    + 23 * HOUR_MILLIS
                    + 59 * MINUTE_MILLIS
                    + 59 * SECOND_MILLIS
                    + 999);
        }
    }

    public static long parseOffset(CharSequence in, int lo, int hi) {
        int p = lo;
        int state = STATE_INIT;
        boolean negative = false;
        int hour = 0;
        int minute = 0;

        try {
            OUT:
            while (p < hi) {
                char c = in.charAt(p);

                switch (state) {
                    case STATE_INIT:
                        switch (c) {
                            case 'U':
                            case 'u':
                                state = STATE_UTC;
                                break;
                            case 'G':
                            case 'g':
                                state = STATE_GMT;
                                break;
                            case 'Z':
                            case 'z':
                                state = STATE_END;
                                break;
                            case '+':
                                negative = false;
                                state = STATE_HOUR;
                                break;
                            case '-':
                                negative = true;
                                state = STATE_HOUR;
                                break;
                            default:
                                if (c >= '0' && c <= '9') {
                                    state = STATE_HOUR;
                                    p--;
                                } else {
                                    return Long.MIN_VALUE;
                                }
                                break;
                        }
                        p++;
                        break;
                    case STATE_UTC:
                        if (p > hi - 2 || !Chars.equalsIgnoreCase(in, p, p + 2, "tc", 0, 2)) {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_SIGN;
                        p += 2;
                        break;
                    case STATE_GMT:
                        if (p > hi - 2 || !Chars.equalsIgnoreCase(in, p, p + 2, "mt", 0, 2)) {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_SIGN;
                        p += 2;
                        break;
                    case STATE_SIGN:
                        switch (c) {
                            case '+':
                                negative = false;
                                break;
                            case '-':
                                negative = true;
                                break;
                            default:
                                return Long.MIN_VALUE;
                        }
                        p++;
                        state = STATE_HOUR;
                        break;
                    case STATE_HOUR:
                        if (c >= '0' && c <= '9' && p < hi - 1) {
                            hour = Numbers.parseInt(in, p, p + 2);
                        } else {
                            return Long.MIN_VALUE;
                        }
                        state = STATE_DELIM;
                        p += 2;
                        break;
                    case STATE_DELIM:
                        if (c == ':') {
                            state = STATE_MINUTE;
                            p++;
                        } else if (c >= '0' && c <= '9') {
                            state = STATE_MINUTE;
                        } else {
                            return Long.MIN_VALUE;
                        }
                        break;
                    case STATE_MINUTE:
                        if (c >= '0' && c <= '9' && p < hi - 1) {
                            minute = Numbers.parseInt(in, p, p + 2);
                        } else {
                            return Long.MIN_VALUE;
                        }
                        p += 2;
                        state = STATE_END;
                        break OUT;
                }
            }
        } catch (NumericException e) {
            return Long.MIN_VALUE;
        }

        switch (state) {
            case STATE_DELIM:
            case STATE_END:
                if (hour > 23 || minute > 59) {
                    return Long.MIN_VALUE;
                }
                int min = hour * 60 + minute;
                long r = ((long) (p - lo) << 32) | min;
                return negative ? -r : r;
            default:
                return Long.MIN_VALUE;
        }
    }

    public static long previousOrSameDayOfWeek(long millis, int dow) {
        int thisDow = getDayOfWeek(millis);
        if (thisDow == dow) {
            return millis;
        }

        if (thisDow < dow) {
            return millis - (7 + (thisDow - dow)) * DAY_MILLIS;
        } else {
            return millis - (thisDow - dow) * DAY_MILLIS;
        }
    }

    public static long toMillis(int y, int m, int d, int h, int mi) {
        return toMillis(y, isLeapYear(y), m, d, h, mi);
    }

    public static long toMillis(int y, boolean leap, int m, int d, int h, int mi) {
        return yearMillis(y, leap) + monthOfYearMillis(m, leap) + (d - 1) * DAY_MILLIS + h * HOUR_MILLIS + mi * MINUTE_MILLIS;
    }

    public static String toString(long millis) {
        StringSink sink = new StringSink();
        DateFormatUtils.appendDateTime(sink, millis);
        return sink.toString();
    }

    /**
     * Calculated start of year in millis. For example of year 2008 this is
     * equivalent to parsing "2008-01-01T00:00:00.000Z", except this method is faster.
     *
     * @param year the year
     * @param leap true if give year is leap year
     * @return millis for start of year.
     */
    public static long yearMillis(int year, boolean leap) {
        int leapYears = year / 100;
        if (year < 0) {
            leapYears = ((year + 3) >> 2) - leapYears + ((leapYears + 3) >> 2) - 1;
        } else {
            leapYears = (year >> 2) - leapYears + (leapYears >> 2);
            if (leap) {
                leapYears--;
            }
        }

        return (year * 365L + (leapYears - DAYS_0000_TO_1970)) * DAY_MILLIS;
    }

    private static boolean checkLen(int p, int lim) throws NumericException {
        if (lim - p > 2) {
            return true;
        }
        if (lim <= p) {
            return false;
        }

        throw NumericException.INSTANCE;
    }

    private static long getTime(long millis) {
        return millis < 0 ? DAY_MILLIS - 1 + (millis % DAY_MILLIS) : millis % DAY_MILLIS;
    }

    private static long toMillis(int y, int m, int d) {
        boolean l = isLeapYear(y);
        return yearMillis(y, l) + monthOfYearMillis(m, l) + (d - 1) * DAY_MILLIS;
    }

    private static void checkChar(CharSequence s, int p, int lim, char c) throws NumericException {
        if (p >= lim || s.charAt(p) != c) {
            throw NumericException.INSTANCE;
        }
    }

    private static void checkRange(int x, int min, int max) throws NumericException {
        if (x < min || x > max) {
            throw NumericException.INSTANCE;
        }
    }

    static {
        long minSum = 0;
        long maxSum = 0;
        for (int i = 0; i < 11; i++) {
            minSum += DAYS_PER_MONTH[i] * DAY_MILLIS;
            MIN_MONTH_OF_YEAR_MILLIS[i + 1] = minSum;
            maxSum += getDaysPerMonth(i + 1, true) * DAY_MILLIS;
            MAX_MONTH_OF_YEAR_MILLIS[i + 1] = maxSum;
        }
    }

}