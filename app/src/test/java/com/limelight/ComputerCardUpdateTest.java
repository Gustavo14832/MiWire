package com.limelight;

import com.limelight.nvstream.http.ComputerDetails;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComputerCardUpdateTest {
    @Test public void refreshesWhenPollingMutatesTheSameHost() {
        ComputerDetails host = new ComputerDetails();
        host.state = ComputerDetails.State.ONLINE;
        PcView.ComputerObject card = new PcView.ComputerObject(host);
        assertFalse(card.update(host));
        host.state = ComputerDetails.State.OFFLINE;
        assertTrue(card.update(host));
        assertFalse(card.update(host));
        host.runningGameId = 42;
        assertTrue(card.update(host));
        host.name = "PC";
        assertTrue(card.update(host));
        ComputerDetails fresh = new ComputerDetails(host);
        assertFalse(card.update(fresh));
        assertSame(fresh, card.details);
    }
}
