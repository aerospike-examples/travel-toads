package com.aerospike.demo.booking.web.dto;

/** POST /bookings request body. */
public final class BookingRequest {
    public String hotelId;
    public String roomId;
    public long checkIn;
    public long checkOut;
    public String guestName;
    public boolean breakfast;
    public boolean flex;
    public boolean demo = false;
}
