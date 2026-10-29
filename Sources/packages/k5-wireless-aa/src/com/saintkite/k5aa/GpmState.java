package com.saintkite.k5aa;

import java.io.IOException;

/** getGPMConnectedMode includes a pending start mode on this pinned OEM ROM. */
public final class GpmState {
    public final int reportedMode;
    public final int sessionStatus;
    public GpmState(int mode,int status) { reportedMode=mode;sessionStatus=status; }
    public int protectedMode() throws IOException {
        if(reportedMode<0 || reportedMode>2 || sessionStatus<0 || sessionStatus>6)
            throw new IOException("Unknown AA state: "+this);
        // Status zero is the session's initial/uninitialized state. Do not infer
        // that any other status is idle, even if projection is not visible yet.
        if(sessionStatus==0)return 0;
        if(reportedMode==0)throw new IOException("Inconsistent AA state: "+this);
        return reportedMode;
    }
    public String toString() { return "reportedMode="+reportedMode+" sessionStatus="+sessionStatus; }
}
