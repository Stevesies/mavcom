/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/


package com.comino.mavcom.control.impl;

import org.mavlink.messages.MAV_COMPONENT;
import org.mavlink.messages.MAV_STATE;
import org.mavlink.messages.MAV_TYPE;
import org.mavlink.messages.lquac.msg_heartbeat;

import com.comino.mavcom.comm.udp.MAVUdpCommNIO;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.model.segment.Status;


public class MAVUdpController extends MAVController implements IMAVController, Runnable {

	private boolean connected;

	public MAVUdpController(String peerAddress, int peerPort, int bindPort, boolean isSITL) {
		super();
		this.isSITL = isSITL;
		this.peerAddress = peerAddress;
		this.peerPort = peerPort;
		this.bindPort = bindPort;
		System.out.println("UDP Controller loaded ("+peerAddress+":"+peerPort+")");
		comm = MAVUdpCommNIO.getInstance(model, peerAddress,peerPort, bindPort);
		model.sys.setStatus(Status.MSP_PROXY, false);
	}

	@Override
	public boolean connect() {

		System.out.print("Try to start..");
		if(this.connected)
			return true;
		
		Thread t = new Thread(this);
		t.setName("UDP Controller");
		t.setDaemon(true);
		t.start();

		return true;
	}

	@Override
	public boolean close() {
		this.connected = false;
		return true;
	}


	@Override
	public boolean isConnected() {
//		if(comm == null)
//			return false;
//		return comm.isConnected();
		return model.sys.isStatus(Status.MSP_CONNECTED);
	}

	@Override
	public void run() {
		
		final msg_heartbeat beat = new msg_heartbeat(2,MAV_COMPONENT.MAV_COMP_ID_OSD);
		beat.type = MAV_TYPE.MAV_TYPE_GCS;
		beat.system_status = MAV_STATE.MAV_STATE_ACTIVE;

		// If not checked here, the thread is started twice (not by connect) ??
		if(connected)
			return;

		this.connected = true;
		System.out.println("UDP connection Thread started");
		while(connected) {
			try {
				//System.out.println(comm.isConnected());
				if(!comm.isConnected()) {
				 comm.close(); comm.open();
				 Thread.sleep(100);
				 continue;
				}
				model.sys.setStatus(Status.MSP_SITL, isSimulation());
				comm.write(beat);
				Thread.sleep(200);
			} catch (Exception e) { try { Thread.sleep(100); } catch (InterruptedException e1) {	} }
		}
		comm.close();
	}
}
