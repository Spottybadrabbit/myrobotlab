package org.myrobotlab.service;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;

import org.myrobotlab.framework.Encoder;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.Service;
import org.myrobotlab.framework.Status;
import org.myrobotlab.logging.Level;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.logging.Logging;
import org.myrobotlab.logging.LoggingFactory;
import org.myrobotlab.serial.VirtualSerialPort.VirtualNullModemCable;
import org.slf4j.Logger;

public class Blender extends Service {

	private static final long serialVersionUID = 1L;

	public final static Logger log = LoggerFactory.getLogger(Blender.class);

	public static final String SUCCESS = "SUCCESS";

	Socket control = null;
	ControlHandler controlHandler = null;
	String host = "localhost";
	Integer controlPort = 8989;
	Integer serialPort = 9191;

	String blenderVersion;
	String expectedBlenderVersion = "0.9";
	
	HashMap<String, VirtualPort> virtualPorts = new HashMap<String, VirtualPort>();
	
	public class VirtualPort {
		public Serial serial;
		public VirtualNullModemCable cable;
	}

	// Socket serial = null; NO

	public class ControlHandler extends Thread {
		Socket socket;
		DataInputStream dis;		
		boolean listening = false;

		public ControlHandler(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			BufferedReader in = null;
			try {
				listening = true;
				//in = socket.getInputStream();
				dis = new DataInputStream(socket.getInputStream());
				in = new BufferedReader(new InputStreamReader(dis));
				while (listening) {
					// handle inbound control messages
					
					//JSONObject json = new JSONObject(in.readLine());
					String json = in.readLine();
					log.info(String.format("%s", json));
					Message msg = Encoder.gson.fromJson(json, Message.class);
					log.info(String.format("msg %s", msg));
					invoke(msg);

				}
			} catch (Exception e) {
				Logging.logException(e);
			} finally {
				try {
					if (in != null)
						in.close();
				} catch (Exception e) {
				}
			}
		}
	}

	public Blender(String n) {
		super(n);
	}

	boolean isConnected() {
		return (control != null) && control.isConnected();
	}

	@Override
	public String getDescription() {
		return "used as a general blender";
	}

	public boolean connect(String host, Integer port) {
		this.host = host;
		this.controlPort = port;
		return connect();
	}

	public boolean connect() {
		try {
			if (control != null && control.isConnected()) {
				info("already connected");
				return true;
			}

			info("connecting to Blender.py %s %d", host, controlPort);
			control = new Socket(host, controlPort);
			controlHandler = new ControlHandler(control);			
			controlHandler.start();

			info("connected - goodtimes");
			return true;
		} catch (Exception e) {
			error(e);
		}
		return false;
	}

	public boolean disconnect() {
		try {
			if (control != null) {
				control.close();
			}
			if (controlHandler != null){
				controlHandler.listening = false;
				controlHandler = null;
			}
			// TODO - run through all serial connections
			return true;
		} catch (Exception e) {
			error(e);
		}
		return false;
	}

	// -------- publish api begin --------
	public void publishDisconnect() {
	}

	public void publishConnect() {
	}

	// -------- publish api end --------
	
	// -------- Blender.py --> callback api begin --------
	public void getVersion() {
		sendMsg("getVersion");
	}
	
	public String onGetVersion(String version) {
		info("blender returned %s", version);
		return version;
	}
	// -------- Blender.py --> callback api end --------



	public String onBlenderVersion(String version) {
		blenderVersion = version;
		if (blenderVersion.equals(expectedBlenderVersion)) {
			info("Blender.py version is %s goodtimes", version);
		} else {
			error("Blender.py version is %s goodtimes", version);
		}

		return version;
	}

	public void sendMsg(String method, Object... data) {
		if (isConnected()) {
			try {
				Message msg = createMessage("Blender.py", method, data);
				OutputStream out = control.getOutputStream();
				String json = Encoder.gson.toJson(msg);
				info("sending %s", json);
				out.write(json.getBytes());
			} catch (Exception e) {
				error(e);
			}
		} else {
			error("not connected");
		}
	}

	@Override
	public Status test() {
		Status status = super.test();
		try {
			// Runtime.start("gui", "GUIService");
			Runtime.start("gui", "GUIService");
			Blender blender = (Blender) Runtime.start(getName(), "Blender");
			if (!blender.connect()) {
				throw new Exception("could not connect");
			}

			blender.getVersion();
			
			Arduino arduino01 = (Arduino) Runtime.start("arduino01", "Arduino");
			
			blender.attach(arduino01);
			
			Servo servo01 = (Servo) Runtime.start("servo01", "Servo");
			
			servo01.attach(arduino01, 7);
			
			servo01.moveTo(90);
			servo01.moveTo(120);
			servo01.moveTo(0);
			
			servo01.sweep();
			servo01.stop();
			servo01.detach();

			blender.getVersion();

		} catch (Exception e) {
			status.addError(e);
		}

		return status;
	}

	public synchronized void attach(Arduino service) {
		// BAH ! STOOPID HACK FOR BAD ARDUINO !!!
		
		// let Blender know we are going
		// to virtualize an Arduino
		sendMsg("attach", service.getName(), service.getSimpleName());
	}
	
	// call back from blender
	public String onAttach(String name){	
		info("onAttach - Blender is ready to attach serial device %s", name);
		// FIXME - make more general - "any" Serial device !!!
		Arduino arduino = (Arduino)Runtime.getService(name);
		if (arduino != null){
			
			int vpn = virtualPorts.size();

			VirtualPort vp = new VirtualPort();
			vp.serial = (Serial) Runtime.start(String.format("%s.UART.%d",arduino.getName(), vpn), "Serial");
			vp.cable = vp.serial.createNullModemCable(String.format("MRL.%d", vpn), String.format("BLND.%d", vpn));
			virtualPorts.put(arduino.getName(), vp);
			vp.serial.connect(String.format("BLND.%d", vpn));
			arduino.connect(String.format("MRL.%d", vpn));
			vp.serial.connectTCPRelay(host, serialPort);
			vp.serial.addRelay(host, serialPort);
			
		} else {
			error("onAttach %s not found", name);
		}
		return name;
	}

	public static void main(String[] args) {
		LoggingFactory.getInstance().configure();
		LoggingFactory.getInstance().setLevel(Level.INFO);

		try {

			Blender blender = (Blender) Runtime.start("blender", "Blender");
			blender.test();

			// Runtime.start("gui", "GUIService");

		} catch (Exception e) {
			Logging.logException(e);
		}
	}

}
