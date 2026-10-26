package com.example.urbanklink.commuter;

import com.example.urbanklink.database.LocalRoute;
import com.example.urbanklink.database.LocalTicket;
import com.example.urbanklink.database.Stop;

public class TicketDisplayData {
    public LocalTicket ticket;
    public Stop startStop;
    public Stop destStop;
    public LocalRoute route;

    public TicketDisplayData(LocalTicket ticket, Stop startStop, Stop destStop, LocalRoute route) {
        this.ticket = ticket;
        this.startStop = startStop;
        this.destStop = destStop;
        this.route = route;
    }
}
