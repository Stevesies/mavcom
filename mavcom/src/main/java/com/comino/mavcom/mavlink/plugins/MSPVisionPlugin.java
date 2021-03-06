package com.comino.mavcom.mavlink.plugins;


import org.mavlink.messages.MAVLinkMessage;
import org.mavlink.messages.lquac.msg_msp_vision;

import com.comino.mavcom.model.segment.Status;
import com.comino.mavcom.model.segment.Vision;

public class MSPVisionPlugin extends MAVLinkPluginBase {

	public MSPVisionPlugin() {
		super(msg_msp_vision.class);
	}

	@Override
	public void received(Object o) {

		msg_msp_vision vision = (msg_msp_vision) o;

		model.vision.vx = vision.vx;
		model.vision.vy = vision.vy;
		model.vision.vz = vision.vz;

		model.vision.gx = vision.gx;
		model.vision.gy = vision.gy;
		model.vision.gz = vision.gz;

		model.vision.px = vision.px;
		model.vision.py = vision.py;
		model.vision.pz = vision.pz;

		model.vision.x = vision.x;
		model.vision.y = vision.y;
		model.vision.z = vision.z;

		model.vision.h= vision.h;
		model.vision.p= vision.p;
		model.vision.r= vision.r;

		model.vision.qual = vision.quality;
		model.vision.errors = (int) vision.errors;

		model.vision.flags = (int) vision.flags;
		model.sys.setSensor(Status.MSP_FIDUCIAL_LOCKED,model.vision.isStatus(Vision.FIDUCIAL_LOCKED));
		model.vision.fps = vision.fps;
		if (model.vision.errors < 5) {
			model.vision.tms = model.sys.getSynchronizedPX4Time_us();
			model.sys.setSensor(Status.MSP_OPCV_AVAILABILITY, true);
		}
	}

}
