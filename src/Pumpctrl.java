import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.util.*;
import javax.comm.*;
import static java.lang.Math.*;

public class Pumpctrl extends Thread implements SerialPortEventListener {
	
	// COM port variables
	static Enumeration portList;
    static CommPortIdentifier portId;
    static SerialPort serialPort;
    
	// log file variables
	static OutputStream outputStream;
	static InputStream inputStream;
	
	// main window containers, buttons, labels, etc
	static JFrame main_frame = new JFrame("Pump Control");
	static ClockView clock_panel = new ClockView();
	static StopWatch infuse_time_panel = new StopWatch();
	static JLabel infuse_start = new JLabel("      Infusion Start Time:  00:00:00");
	static JPanel pump_status_panel = new JPanel();
	static JLabel pump_status_label = new JLabel();
	static JLabel pump_program_version_label = new JLabel("program version 1.1.4, Apr 15, 2013");
	static JPanel left_panel = new JPanel();
	static JPanel subject_info = new JPanel();
	static JLabel subject_title = new JLabel("Subject Info");
	static JPanel pump_info = new JPanel();
	static JProgressBar infuse_progress = new JProgressBar();
	static JPanel ctrl_panel = new JPanel();
	static JTextArea serial_io = new JTextArea();
	static JScrollPane io_scroll;
	static JTabbedPane jtp = new JTabbedPane();
	static JEditorPane info = null;
	static StringBuffer io_record = new StringBuffer();
	static JFileChooser chooser = new JFileChooser();
	static File log;
	static PrintStream log_stream;

	static JTable subject_table = new JTable(6,2);
	
	static JButton prime_button = new JButton("Prime");
	static JButton setup_button = new JButton("Setup");
	static JButton infuse_button = new JButton("Start");
	static JButton pause_button = new JButton("Pause Infusion");
	static JButton bathroom_button = new JButton("Bathroom Break");
	static JButton change_button = new JButton("Change Syringe / Inject Raclopride");
	static JButton connect_button = new JButton("Connect to pump");
	static JButton add_time_button = new JButton("Add Time");
	static JButton end_button = new JButton("End Infusion");
	
	// infusion constatnts
	static double load_decay_const = 50;
	static double JM_load_constant = 2.7;
	static double target_plasma = 1200.0;  // ng/ml
	static double pharmacy = 2.0; // mg/ml
	static double vod = 0;
	static double clearance = 0;
	static Double r_part1 = new Double(0);
	static Double r_part2 = new Double(0);
	static Double r_check1 = new Double(0);
	static Double r_check2 = new Double(0);
	static Double[] rate;
	static int change_stat = 0;

	static Double d_int = new Double(0);
	static Double total_time = new Double(0);
	static Double running_time = new Double(0);
	static int rate_num = 0;
	static int rate_indx = 0;
	
	// setup info variables
	static String cnda_id;
	static Integer visit_day = new Integer(0);
	static Double age = new Double(0);
	static Double weight = new Double(0);
	static Double mass = new Double(0);
	static Double dia = new Double(0);

	// boolean control variables
	static boolean infuse_go = false;
	static boolean infusion_running = false;
	static boolean pump_response_echo = true;
	static boolean infuse_timer = false;
	static boolean time_added = false;
	
	static int exit_button;
	static int end_button_press;
	
	static Font bigFont = new Font("arial", Font.BOLD, 18);
	
	public void serialEvent(SerialPortEvent event) {
		switch(event.getEventType()) {
			case SerialPortEvent.BI:
			case SerialPortEvent.OE:
			case SerialPortEvent.FE:
			case SerialPortEvent.PE:
			case SerialPortEvent.CD:
			case SerialPortEvent.CTS:
			case SerialPortEvent.DSR:
			case SerialPortEvent.RI:
			case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
				break;
			case SerialPortEvent.DATA_AVAILABLE:
				byte[] readBuffer = new byte[1];

				try {
					while (inputStream.available() > 0) {
						int numBytes = inputStream.read(readBuffer);
						if ( pump_response_echo ) {
							serial_io.append(new String(readBuffer));
							// serial_io.scrollRectToVisible(rect);
						}
						io_record.append(new String(readBuffer));
						try {
							log_stream.print(new String(readBuffer));
						} catch (Exception e) {}
					}
				} catch (IOException e) {}
				break;
		}
	}
	
	public void setup_main_window(){

		// setup main window
		main_frame.setSize(780,450);
		main_frame.setResizable(false);
		main_frame.getContentPane().setLayout( new FlowLayout(FlowLayout.CENTER,10,10) );
		main_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		WindowListener l = new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				if ( infuse_timer ) {
					exit_button = JOptionPane.showConfirmDialog(main_frame,"Are you sure you want to exit?","Exit?",JOptionPane.OK_CANCEL_OPTION);
					if ( exit_button == JOptionPane.OK_OPTION ) {
						JOptionPane.showMessageDialog(main_frame,"OK, exiting now","Exiting",JOptionPane.WARNING_MESSAGE);
						System.exit(0);
					}
				} else { System.exit(0); }
			}
		};
		main_frame.addWindowListener(l);
		
		// setup border for panels
		Border etched_border;
		etched_border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		
		// setup upper left panel
		left_panel.setPreferredSize(new Dimension(270,190));

		// add clock to upper left panel
		Thread clock_thread = new Thread(clock_panel);
		clock_thread.start();
		clock_panel.setPreferredSize(new Dimension(270,30));
		left_panel.add(clock_panel);
		
		// setup subject info panel
		subject_info.setBorder(etched_border);
		subject_info.setLayout( new FlowLayout() );
		subject_title.setFont(new Font("Dialog", Font.BOLD, 14));
		subject_title.setPreferredSize(new Dimension(270,25));
		subject_title.setHorizontalAlignment(JLabel.CENTER);
		subject_info.add(subject_title);
		subject_table.getColumnModel().getColumn(0).setPreferredWidth(120);
		subject_table.getColumnModel().getColumn(1).setPreferredWidth(90);
		subject_table.setValueAt("Age:",0,0);
		subject_table.setValueAt("Weight:",1,0);
		subject_table.setValueAt("Target plasma conc.:",2,0);
		subject_table.setValueAt("Target volume:",3,0);
		subject_table.setValueAt("Infusion length:",4,0);
		subject_table.setValueAt("Rate change interval:",5,0);
		subject_table.doLayout();
		subject_table.setEnabled(false);
		subject_info.add(subject_table);
		subject_info.setPreferredSize(new Dimension(270,150));
		left_panel.add(subject_info);
		main_frame.add(left_panel);
		
		// setup pump info panel
		pump_info.setLayout(new FlowLayout(FlowLayout.CENTER,0,10));
		pump_info.setBorder(etched_border);
		infuse_start.setPreferredSize(new Dimension(310,30));
		infuse_start.setHorizontalAlignment(JLabel.CENTER);
		pump_info.add(infuse_start);
		infuse_start.setFont(bigFont);
		infuse_time_panel.reset();
		pump_info.add(infuse_time_panel);
		pump_status_panel.setBackground(Color.RED);
		pump_status_panel.setPreferredSize(new Dimension(275,30));
		pump_status_label.setText("Pump status: stopped");
		pump_status_panel.add(pump_status_label);
		pump_info.add(pump_status_panel);
		infuse_progress.setString("Infusion progress");
		infuse_progress.setStringPainted(true);
		infuse_progress.setPreferredSize(new Dimension(275,25));
		pump_info.add(infuse_progress);
		pump_info.setPreferredSize(new Dimension(470,190));
		main_frame.add(pump_info);

		// setup pump control panel
		/* no prime button for now
		ActionListener a = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				// prime();
			}
		};
		prime_button.addActionListener(a);
		prime_button.setToolTipText("Press to prime the line");
		ctrl_panel.add(prime_button);
		prime_button.setEnabled(false);
		*/
		
		ActionListener b = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				setup();
			}
		};
		setup_button.addActionListener(b);
		setup_button.setToolTipText("Press to do setup steps");
		ctrl_panel.add(setup_button);

		ActionListener c = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				infusion_running = true;
				Infusion infusion = new Infusion();
				infusion.start();
				infuse_button.setEnabled(false);
				prime_button.setEnabled(false);
				setup_button.setEnabled(false);
				pause_button.setEnabled(true);
				bathroom_button.setEnabled(true);
				change_button.setEnabled(true);
				// add_time_button.setEnabled(true);
				end_button.setEnabled(true);
			}
		};
		infuse_button.addActionListener(c);
		infuse_button.setToolTipText("Press to start the infusion");
		infuse_button.setEnabled(false);
		ctrl_panel.add(infuse_button);
		
		/* no pause button for now
		ActionListener d = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				PauseInfusion pause_infusion = new PauseInfusion();
				pause_infusion.start();
			}
		};
		pause_button.setEnabled(false);
		pause_button.addActionListener(d);
		pause_button.setToolTipText("Press to pause infusion");
		ctrl_panel.add(pause_button);
		*/
		
		ActionListener f = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				BathroomBreak bathroom_break = new BathroomBreak();
				bathroom_break.start();
			}
		};
		bathroom_button.setEnabled(false);
		bathroom_button.addActionListener(f);
		bathroom_button.setToolTipText("Press to go on a bathroom break");
		// ctrl_panel.add(bathroom_button);
		
		ActionListener g = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				ChangeSyringe change = new ChangeSyringe();
				change.start();
			}
		};
		change_button.setEnabled(false);
		change_button.addActionListener(g);
		change_button.setToolTipText("Press to change syringe");
		ctrl_panel.add(change_button);
		
		ActionListener h = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				establish_com();
			}
		};
		connect_button.setEnabled(true);
		connect_button.addActionListener(h);
		connect_button.setToolTipText("Press to connect to pump");
		ctrl_panel.add(connect_button);
		
		ActionListener i = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				add_time();
			}
		};
		add_time_button.setEnabled(false);
		add_time_button.addActionListener(i);
		add_time_button.setToolTipText("Press to add time to the end of the infusion");
		// ctrl_panel.add(add_time_button);
		
		ActionListener j = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				end_button_press = JOptionPane.showConfirmDialog(main_frame,"Are you sure you want to end the infusion early?","End early?",JOptionPane.OK_CANCEL_OPTION);
				if ( end_button_press == JOptionPane.OK_OPTION ) {
						JOptionPane.showMessageDialog(main_frame,"OK, ending now","Ending early",JOptionPane.WARNING_MESSAGE);
						infusion_running = false;
					}
			}
		};
		end_button.setEnabled(false);
		end_button.addActionListener(j);
		end_button.setToolTipText("Press to end the infusion");
		ctrl_panel.add(end_button);
		
		
		ctrl_panel.setPreferredSize(new Dimension(750,41));
		ctrl_panel.setBorder(etched_border);
		main_frame.add(ctrl_panel);
		
		/* setup instructions panel
		try {
			info = new JEditorPane("url:http://www.nil.wustl.edu/labs/kevin/man/kjb_faq.html");
		} catch (Exception e) { System.out.println("Error: " + e); }
		info.setEditable(false);
		JScrollPane info_scroll = new JScrollPane(info);
		info_scroll.setPreferredSize(new Dimension(700,153)); */

		io_scroll = new JScrollPane(serial_io);
		io_scroll.setPreferredSize(new Dimension(750,153));
		io_scroll.setWheelScrollingEnabled(true);
		
		main_frame.add(io_scroll);
		
		// pump_program_version_label.setFont(new Font("Dialog", Font.BOLD, 8));
		// main_frame.add(pump_program_version_label);
		
		main_frame.setVisible(true);
	}

	public static void setup(){
		JLabel q1 = new JLabel("Enter subject ID: ");
		JLabel q2 = new JLabel("Enter visit day: ");
		JLabel q3 = new JLabel("Enter subject's age(yrs): ");
		JLabel q4 = new JLabel("Enter subject's weight(lbs): ");
		JLabel q6 = new JLabel("Enter the time interval for the pump rate change(sec): ");
		JLabel q7 = new JLabel("Enter the total time of the scan(min): ");
		JLabel q8 = new JLabel("Enter the diameter of the pump syringe(mm): ");
		JLabel q9 = new JLabel("Enter the target plasma concentration (ng/mL): ");
		String title1 = "Enter subject ID";
		String title2 = "Enter visit day";
		String title3 = "Enter age";
		String title4 = "Enter weight";
		String title6 = "Enter rate change interval";
		String title7 = "Enter total time";
		String title8 = "Enter syringe diameter";
		String title9 = "Enter target plasma conc.";
		String a1 = new String();
		String a2 = new String();
		String a3 = new String();
		String a4 = new String();
		Object a6 = new Object();
		Object a7 = new Object();
		Object a8 = new Object();
		Object a9 = new Object();
		int a10 = 0;
		
		while( true ){
			a1 = JOptionPane.showInputDialog(main_frame,q1,title1,JOptionPane.QUESTION_MESSAGE);
			try {
				cnda_id = new String(a1);
			} catch (Exception e) {}
			if ( cnda_id.length() > 2 ) break;
		}
		
		while( true ){
			a2 = JOptionPane.showInputDialog(main_frame,q2,title2,JOptionPane.QUESTION_MESSAGE);
			try {
				visit_day = visit_day.valueOf(a2);
			} catch (Exception e) {}
			if ( visit_day > 0 && visit_day < 4 ) break;
		}
		
		while( true ){
			a3 = JOptionPane.showInputDialog(main_frame,q3,title3,JOptionPane.QUESTION_MESSAGE);
			try {
				age = age.valueOf(a3);
			} catch (Exception e) {}
			if ( age > 17 && age < 57 ) break;
		}
		
		while( true ){
			a4 = JOptionPane.showInputDialog(main_frame,q4,title4,JOptionPane.QUESTION_MESSAGE);
			try {
				weight = weight.valueOf(a4);
			} catch (Exception e) {}
			if ( weight > 80 && weight < 350 ) break;
		}
		
		while( true ){
			a6 = JOptionPane.showInputDialog(main_frame,q6,title6,JOptionPane.QUESTION_MESSAGE,null,null,60);
			try {
				d_int = Double.parseDouble(a6.toString());
			} catch (Exception e) {}
			if ( d_int >= 5 && d_int <= 120 ) break;
		}
		
		while( true ){
			a7 = JOptionPane.showInputDialog(main_frame,q7,title7,JOptionPane.QUESTION_MESSAGE,null,null,180);
			try {
				total_time = Double.parseDouble(a7.toString());
			} catch (Exception e) {}
			if ( total_time > 100 && total_time <= 240 ) break;
		}

		while( true ){
			a8 = JOptionPane.showInputDialog(main_frame,q8,title8,JOptionPane.QUESTION_MESSAGE,null,null,26.6);
			try {
				dia = Double.parseDouble(a8.toString());
			} catch (Exception e) {}
			if ( dia > 0 && dia <= 240 ) break;
		}
		
		while( true ){
			a9 = JOptionPane.showInputDialog(main_frame,q9,title9,JOptionPane.QUESTION_MESSAGE,null,null,1200.0);
			try {
				target_plasma = Double.parseDouble(a9.toString());
			} catch (Exception e) {}
			if ( target_plasma > 100 && target_plasma <= 1200 ) break;
		}

		chooser.setDialogTitle("Choose a log file");
		chooser.setSelectedFile(new File(System.getProperty("user.dir"), cnda_id + "_v" + visit_day + "_pumplog.txt"));
		while ( true ){
			try {
				a10 = chooser.showSaveDialog(main_frame);
			} catch (Exception e) {}
			if ( a10 == JFileChooser.APPROVE_OPTION ) break;
		}
		
		log = chooser.getSelectedFile();
		try {
			log_stream = new PrintStream( log.getAbsolutePath() );
		} catch (Exception e) {}
		mass = weight/2.2;
		subject_table.setValueAt(target_plasma + " ng/mL",2,1);
		subject_table.setValueAt(total_time + " min",4,1);
		subject_table.setValueAt(d_int + " sec",5,1);
		total_time = total_time*60;
		rate_num = new Double((total_time / d_int )).intValue();
		rate = new Double[rate_num];
		vod = 1052 + 0.8172*(age - 21.5);
		clearance = 5.693 + (21.5 - age)*0.04813;
		r_part1 = (target_plasma*mass)/pharmacy;
		r_part2 = (60*JM_load_constant*vod*load_decay_const)/(1200*1078*70*d_int);
		r_check2 = 0.000001*clearance;
		set_rates();
		infuse_progress.setMinimum(0);
		infuse_progress.setMaximum(total_time.intValue());
		subject_table.setValueAt(age.toString() + " yrs",0,1);
		subject_table.setValueAt(weight.toString() + " lbs",1,1);
		double vol_display = vol_delivered(total_time.intValue());
		vol_display = vol_display*100;
		long vol_display2 = round(vol_display);
		vol_display = (new Double(vol_display2))/100;
		subject_table.setValueAt(vol_display + " mL",3,1);
		
		pump_write("dia " + dia.toString() + "\r",true);
		pump_write("dia\r",true);
		infuse_button.setEnabled(true);
		
	}

	public void establish_com(){
		String[] button_choices = {"Retry", "Exit", "Debug"};
		
		String connect_q = "There was a problem in detecting the pump, please make sure\nit is connected properly and turned on, then click Retry.";
		
		get_coms: while ( true ) {
			portList = CommPortIdentifier.getPortIdentifiers();
			int connected = -1;
			// iterate over port list
			while (portList.hasMoreElements()) {
           		portId = (CommPortIdentifier) portList.nextElement();
				// JOptionPane.showMessageDialog(main_frame, "looking for pump on portID = " + portId.getName(), "Establish Connection", JOptionPane.INFORMATION_MESSAGE);
           		if (portId.getPortType() == CommPortIdentifier.PORT_SERIAL) {
					// JOptionPane.showMessageDialog(main_frame, portId.getName()+ " is a PORT_SERIAL", "Establish Connection", JOptionPane.INFORMATION_MESSAGE);
					try {
                   		serialPort = (SerialPort) portId.open("Pumpctrl", 2000);
           			} catch (PortInUseException e) {System.out.print("problem with comm PortInUseException\n");}
					
           			try {
           				outputStream = serialPort.getOutputStream();
						inputStream = serialPort.getInputStream();
               		} catch (IOException e) {System.out.print("problem with comm IOException\n");}
					// JOptionPane.showMessageDialog(main_frame, "outputStream.toString() = " + outputStream.toString(), "Establish Connection", JOptionPane.INFORMATION_MESSAGE);
					try {
                    	serialPort.setSerialPortParams(2400,
                    	SerialPort.DATABITS_8,
                    	SerialPort.STOPBITS_2,
                    	SerialPort.PARITY_NONE);
               		} catch (UnsupportedCommOperationException e) {System.out.print("problem with comm UnsupportedCommOperationException\n");}
					// JOptionPane.showMessageDialog(main_frame, "baud rate = " + serialPort.getBaudRate(), "Establish Connection", JOptionPane.INFORMATION_MESSAGE);
					try {
						serialPort.addEventListener(this);
					} catch (TooManyListenersException e) {System.out.print("problem with comm TooManyListenersException\n");}
					serialPort.notifyOnDataAvailable(true);
					
					try {
						Thread.sleep(100);
					} catch (Exception e) {}
					
					
					
					// try to write to the pump
					pump_write("mod pmp\r",false);
					// pump_write("mod vol\r",false);
					pump_write("mod\r",false);
					
					try {
						Thread.sleep(100);
					} catch (Exception e) {}
					
					// JOptionPane.showMessageDialog(main_frame, "io_record = " + io_record, "Establish Connection", JOptionPane.INFORMATION_MESSAGE);
					
					if ( io_record.indexOf("PUMP") != -1 ) {
						JOptionPane.showMessageDialog(main_frame,"Pump connection established on " + portId.getName(),"Got connection",JOptionPane.INFORMATION_MESSAGE);
						break get_coms;
					}
            	}
        	}
			
			try {
				connected = JOptionPane.showOptionDialog(main_frame,connect_q,"Error",JOptionPane.OK_CANCEL_OPTION,JOptionPane.ERROR_MESSAGE,null,button_choices,null);
			} catch (Exception e) {}
			switch (connected) {
				case 0: break;
				case 1: System.exit(1);
				case 2: break get_coms;
			}
		}
	}
	
	public static void pump_write(String cmd, boolean echo) {
		int i = 0;

		if ( echo ) {
			serial_io.append(cmd);
			io_record.append(cmd);
		}
		
		try {
			log_stream.print(cmd);
		} catch (Exception e) {}
		
		char cmd_send[] = new char[cmd.length()];
		
		cmd.getChars(0,cmd.length(),cmd_send,0);
		try {
            for (i = 0; i < cmd.length(); i++ ){
				outputStream.write(cmd_send[i]);
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {}
			}
        } catch (Exception e) {}
		try { Thread.sleep(100); } catch (Exception e) {}
	}
	
	public static void set_rates() {
		int i = 0;
		for ( i = 0; i < rate_num; i++ ) {
			r_check1 = r_part2*(exp(-(d_int*i)/(load_decay_const*60.0)) - exp(-(((d_int*i)+d_int)/60.0)/load_decay_const));
			if ( r_check1.compareTo(r_check2) > 0 ){
				rate[i] = r_part1*r_check1;
			} else {
				rate[i] = r_part1*r_check2;
			}
		}
	}

	public static double vol_delivered( int sec ) {
		double ans = 0;
		int i = 0;
		int j = 0;
		int step = d_int.intValue();
		for ( i = 0; i < sec; i++ ) {
			ans += (rate[j])/60;
			if ( i == step ) {
				step += d_int.intValue();
				j++;
			}
		}
		return ans;
	}
	
	public void run() {
		setup_main_window();
		// make connection a button instead
		// establish_com();
	}
	
	public static void add_time() {
		// add some time to the end of the infusion
		time_added = true;
	}
	
	public static void main(String[] args) {
		Pumpctrl pump = new Pumpctrl();
		pump.start();
	}
}

class Infusion extends Thread {
	Calendar c1 = Calendar.getInstance();
	Calendar c2;
	Calendar start_time;
	Calendar end_time;
	Thread infuse_time = new Thread(Pumpctrl.infuse_time_panel);
	 
	public void run() {
		Pumpctrl.log_stream.println(Pumpctrl.pump_program_version_label.getText());
		Pumpctrl.pump_write("mod pmp\r",false);
		Pumpctrl.pump_write("mod\r",false);
		Pumpctrl.pump_write("rat " + Pumpctrl.rate[Pumpctrl.rate_indx].toString().substring(0,6) + " mm\r",true);
		Pumpctrl.pump_write("run\r",true);
		start_time = Calendar.getInstance();
		infuse_time.start();
		Pumpctrl.infuse_start.setText("      Infusion Start Time:  " + getTimeAsString(start_time));
		Pumpctrl.log_stream.println("Infusion began at " + getTimeAsString(start_time));
		Pumpctrl.log_stream.println("");
		Pumpctrl.log_stream.println("Setup info:");
		Pumpctrl.log_stream.println("Subject: " + Pumpctrl.cnda_id);
		Pumpctrl.log_stream.println("Visit: " + Pumpctrl.visit_day.toString());
		Pumpctrl.log_stream.println("Age: " + Pumpctrl.age.toString());
		Pumpctrl.log_stream.println("Weight: " + Pumpctrl.weight.toString());
		Pumpctrl.log_stream.println("Syringe diameter: " + Pumpctrl.dia.toString());
		Pumpctrl.log_stream.println("Rate change interval (sec): " + Pumpctrl.d_int.toString());
		Pumpctrl.log_stream.println("Total infusion time (min): " + (Pumpctrl.total_time/60));
		Pumpctrl.log_stream.println("");		
		Pumpctrl.log_stream.print("Target volume to be infused (if infusion runs for " + (Pumpctrl.total_time/60) + " min) = ");
		Pumpctrl.log_stream.println(Pumpctrl.vol_delivered(Pumpctrl.total_time.intValue()));
		c1 = start_time;
		c1.add(Calendar.SECOND,Pumpctrl.d_int.intValue());
		c1.add(Calendar.MILLISECOND,-1);
		Pumpctrl.running_time = Pumpctrl.running_time + Pumpctrl.d_int;
		Pumpctrl.rate_indx++;
		Pumpctrl.infuse_go = true;
		Pumpctrl.infuse_timer = true;
		Pumpctrl.pump_status_panel.setBackground(Color.GREEN);
		Pumpctrl.pump_status_label.setText("Pump status: infusing");
		Pumpctrl.infuse_time_panel.reset();
		while ( Pumpctrl.running_time <= Pumpctrl.total_time && Pumpctrl.infusion_running) {
			if ( Pumpctrl.infuse_time_panel.time1.after(c1)) {
				if ( Pumpctrl.infuse_go && Pumpctrl.running_time < Pumpctrl.total_time ) {
					c2 = Calendar.getInstance();
					c2.add(Calendar.SECOND,10);
					Pumpctrl.serial_io.append(Pumpctrl.infuse_time_panel.getTimeAsString());
					Pumpctrl.log_stream.println(Pumpctrl.infuse_time_panel.getTimeAsString());
					while ( c2.after(Calendar.getInstance()) ){
						Pumpctrl.pump_write("rat " + Pumpctrl.rate[Pumpctrl.rate_indx].toString().substring(0,6) + " mm\r",false);
						try { Thread.sleep(100); } catch (Exception c) {}
						Pumpctrl.pump_write("rat\r",false);
						try { Thread.sleep(100); } catch (Exception c) {}
						if ( Pumpctrl.io_record.indexOf(Pumpctrl.rate[Pumpctrl.rate_indx].toString().substring(0,6) + " ml/mn") != -1 ) {
							Pumpctrl.pump_write("del\r",true);
							break;
						}
					}
				}
				Pumpctrl.infuse_progress.setValue(Pumpctrl.running_time.intValue());
				Pumpctrl.running_time = Pumpctrl.running_time + Pumpctrl.d_int;
				Pumpctrl.rate_indx++;
				c1.add(Calendar.SECOND,Pumpctrl.d_int.intValue());
				Pumpctrl.io_scroll.getVerticalScrollBar().setValue(100);
			}
		}
		if ( Pumpctrl.time_added ) {
			// add some time, then end
		} else {
			end_infusion(Pumpctrl.infusion_running);	
		}
	}
	
	protected String getDigitsAsString(int i) {
        	String str = Integer.toString(i);
        	if (i<10) return "0"+str;
        	return str;
    	}

	public String getTimeAsString(Calendar cal) {
		return getDigitsAsString(cal.get(Calendar.HOUR_OF_DAY)) + ":"
                     + getDigitsAsString(cal.get(Calendar.MINUTE))  + ":"
                     + getDigitsAsString(cal.get(Calendar.SECOND));
	}
	
	public void end_infusion(boolean infusion_running) {
		Pumpctrl.pump_write("stp\r",true);
		Pumpctrl.infuse_time_panel.pauseWatch();
		Pumpctrl.pump_status_panel.setBackground(Color.RED);
		Pumpctrl.pump_status_label.setText("Pump status: stopped");
		end_time = Calendar.getInstance();
		if ( infusion_running ) {
			Pumpctrl.log_stream.println("Infusion ended at " + getTimeAsString(end_time));
		} else {
			Pumpctrl.log_stream.println("Infusion ended early by user at " + getTimeAsString(end_time));
		}
		Pumpctrl.pump_write("del\r",true);
		Pumpctrl.log_stream.println();
		Pumpctrl.log_stream.close();
		Pumpctrl.infuse_go = false;
		Pumpctrl.infuse_timer = false;
	}
	
}

class PauseInfusion extends Thread {
	Integer change_time1 = new Integer(0);
	Integer change_time2 = new Integer(0);
	Double catch_up_rate = new Double(0);
	int resume_a = JOptionPane.CANCEL_OPTION;

	public void run() {
		// pause pump
		Pumpctrl.infuse_go = false;
		Pumpctrl.pump_write("stp\r",true);
		change_time1 = Pumpctrl.infuse_time_panel.sec_elapsed;
		Pumpctrl.pump_write("del\r",true);
		Pumpctrl.io_record.append("Volume delivered since last reset = " + Pumpctrl.vol_delivered(change_time1) + " ml\n\n");
		Pumpctrl.pump_status_panel.setBackground(Color.YELLOW);
		Pumpctrl.pump_status_label.setText("Pump status: Infusion paused, stopped");

		// bring up continue button
		String resume_q = "When you are ready to resume the infusion, click OK";
		while ( resume_a == JOptionPane.CANCEL_OPTION) {
			try {
				resume_a = JOptionPane.showOptionDialog(Pumpctrl.main_frame, resume_q, "Ready to resume infusing?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);
			} catch (Exception e){}
		}

		// calculate new catch-up rate and start catch-up
		change_time2 = Pumpctrl.infuse_time_panel.sec_elapsed;
		catch_up_rate = (Pumpctrl.vol_delivered(change_time2.intValue() + 60) - Pumpctrl.vol_delivered(change_time1.intValue()));
		Pumpctrl.pump_write("mod pmp\r",false);
		Pumpctrl.pump_write("mod\r",false);
		Pumpctrl.pump_write("rat " + catch_up_rate.toString().substring(0,6) + " mm\r",true);
		Pumpctrl.pump_write("run\r",true);
		Pumpctrl.pump_status_panel.setBackground(Color.GREEN);
		Pumpctrl.pump_status_label.setText("Pump status: infusing (catch-up rate)");
		change_time2 = change_time2 + 60;
		while ( ! change_time2.equals(change_time1) ) {
			try {
				this.sleep(100);
			} catch (Exception e) {}
			change_time1 = Pumpctrl.infuse_time_panel.sec_elapsed;
		}

		// rejoin normal rate
		Pumpctrl.pump_write("rat " + Pumpctrl.rate[Pumpctrl.rate_indx - 1].toString().substring(0,6) + " mm\r",true);
		Pumpctrl.infuse_go = true;
		Pumpctrl.pump_status_label.setText("Pump status: infusing");
	}
}

class BathroomBreak extends Thread {
	Integer change_time1 = new Integer(0);
	Integer change_time2 = new Integer(0);
	Double break_rate = new Double(0);
	Integer break_index = new Integer(0);
	Double catch_up_rate = new Double(0);
	Object break_a = new Object();
	String break_q = "How long would you like the break to be?";
	Integer break_length = new Integer(0);
	Double break_end_vol = new Double(0);
	Double reconnect_vol = new Double(0);
	
	public void run() {
		// set break time, default 7 minutes
		while ( true ) {
			break_a = JOptionPane.showInputDialog(Pumpctrl.main_frame, break_q, "Input break length (1-10 minutes)", JOptionPane.QUESTION_MESSAGE, null, null, "7");
			try {
				break_length = Integer.parseInt(break_a.toString());
			} catch (Exception e) {}
			if ( break_length > 0 && break_length < 11 ) break;
		}
		// calculate and set new rate
		Pumpctrl.infuse_go = false;
		Pumpctrl.pump_write("del\r",true);
		change_time1 = Pumpctrl.infuse_time_panel.sec_elapsed;
		Pumpctrl.serial_io.append("Bathroom break at " + Pumpctrl.infuse_time_panel.getTimeAsString() + "\n");
		Pumpctrl.log_stream.println("Bathroom break at " + Pumpctrl.infuse_time_panel.getTimeAsString());
		
		// for now, we'll just assume the infusion is at the correct place, instead of calculating based on "del" volume. This is to account for a change syringe before a bathroom break, which doesn't work in that case
		Pumpctrl.serial_io.append("target Volume delivered since last reset = " + Pumpctrl.vol_delivered(change_time1) + " ml\n\n");
		Pumpctrl.log_stream.println("target Volume delivered since last reset = " + Pumpctrl.vol_delivered(change_time1) + " ml");
		change_time2 = change_time1 + break_length*60;
		break_rate = (Pumpctrl.vol_delivered(change_time2.intValue()) - Pumpctrl.vol_delivered(change_time1.intValue()))/break_length.doubleValue();
		Pumpctrl.pump_write("rat " + break_rate.toString().substring(0,6) + " mm\r",true);
		Pumpctrl.pump_status_label.setText("Pump status: Bathroom break, <" + break_length.toString() + " minutes left");
		try { this.sleep(1000); } catch (Exception e) {}
		Pumpctrl.pump_write("rat\r",false);
		try { this.sleep(100); } catch (Exception e) {}
		break_index = Pumpctrl.io_record.lastIndexOf(break_rate.toString().substring(0,6) + " ml/mn");
		// JOptionPane.showMessageDialog(Pumpctrl.main_frame,"break_index = " + break_index.toString());

		// wait for break to be over
		break_length = break_length - 1;
		while ( true ) {
			if ( Pumpctrl.infuse_time_panel.sec_elapsed > change_time2 ) {
				break;
			} else {
				try {
					this.sleep(500);
					if ( change_time2 - Pumpctrl.infuse_time_panel.sec_elapsed < break_length*60 && break_length > 0) {
						if ( break_length > 1 ) {
							Pumpctrl.pump_status_label.setText("Pump status: Bathroom break, <" + break_length.toString() + " minutes left");
						} else {
							Pumpctrl.pump_status_label.setText("Pump status: Bathroom break, <" + break_length.toString() + " minute left");
						}
						break_length = break_length - 1;
						this.sleep(50000);
					}
				} catch (Exception e) {}
			}
		}
		Pumpctrl.pump_status_label.setText("Pump status: Ending bathroom break...");
		
		// check that pump is connected again
		while ( true ) {
			Pumpctrl.pump_write("del\r",false);
			try { this.sleep(100); } catch (Exception e) {}
			if ( Pumpctrl.io_record.lastIndexOf(".") > break_index + 4 ) {
				// read volume delivered
				// reconnect_vol = Double.parseDouble(Pumpctrl.io_record.substring(Pumpctrl.io_record.lastIndexOf(".") - 2,Pumpctrl.io_record.lastIndexOf(".")+5));
				
				// for now, we'll just assume the infusion is at the correct place, instead of calculating based on "del" volume. This is to account for a change syringe before a bathroom break, which doesn't work in that case
				// break_end_vol = Pumpctrl.vol_delivered(change_time2.intValue());
				
				/* JOptionPane.showMessageDialog(Pumpctrl.main_frame,"Volume = " + break_end_vol.toString());
				break_end_vol = break_end_vol/2;
				JOptionPane.showMessageDialog(Pumpctrl.main_frame,"Volume/2 = " + break_end_vol.toString()); */
				break;
			} else {
				Pumpctrl.pump_status_label.setText("Pump status: infusing (please reconnect)");
				try { this.sleep(5000); } catch (Exception e) {}
			}
		}
		
		// calculate and start correction rate (2 minutes)
		// for now, we'll just assume the infusion is at the correct place, instead of calculating based on "del" volume. This is to account for a change syringe before a bathroom break, which doesn't work in that case
		change_time2 = Pumpctrl.infuse_time_panel.sec_elapsed;
		catch_up_rate = (Pumpctrl.vol_delivered(change_time2 + 120) - (break_rate*((change_time2-change_time1)/60)+Pumpctrl.vol_delivered(change_time1.intValue())))/2;
		Pumpctrl.pump_write("rat " + catch_up_rate.toString().substring(0,6) + " mm\r",true);
		Pumpctrl.pump_write("rat\r",true);
		Pumpctrl.pump_status_label.setText("Pump status: infusing (correction rate)");
		change_time2 = change_time2 + 120;
		while ( change_time2 > Pumpctrl.infuse_time_panel.sec_elapsed ) {
			try { this.sleep(1000); } catch (Exception e) {}
		}

		// rejoin normal rate (after 2 minutes of catch-up)
		Pumpctrl.pump_write("rat " + Pumpctrl.rate[Pumpctrl.rate_indx - 1].toString().substring(0,6) + " mm\r",true);
		Pumpctrl.infuse_go = true;
		Pumpctrl.pump_status_label.setText("Pump status: infusing");
	}
}

class ChangeSyringe extends Thread {
	Integer change_time1 = new Integer(0);
	Integer change_time2 = new Integer(0);
	Double catch_up_rate = new Double(0);
	int flush_a = JOptionPane.CANCEL_OPTION;
	int resume_a = 0;
	int catch_up_time = 0;
	Object[] resume_options = {"120","60"};
	Object[] flush_options = {"FLUSH","SKIP FLUSH","Cancel"};
	String flush_q1 = "When the new syringe is loaded, the stopcock is turned closed-to-subject,\n and you are ready to flush the line, click FLUSH\n\nOR, IF YOU WANT TO SKIP FLUSHING FOR INJECTING RACLOPRIDE, CLICK SKIP FLUSH";

	public void run() {
		Pumpctrl.infuse_go = false;
		Pumpctrl.pump_write("stp\r",true);
		change_time1 = Pumpctrl.infuse_time_panel.sec_elapsed;
		Pumpctrl.pump_write("del\r",true);
		Pumpctrl.io_record.append("Volume delivered from first syringe = " + Pumpctrl.vol_delivered(change_time1) + " ml\n\n");
		Pumpctrl.pump_status_panel.setBackground(Color.YELLOW);
		Pumpctrl.pump_status_label.setText("Pump status: changing syringes, stopped");
		Pumpctrl.change_stat++;
		Pumpctrl.change_button.setEnabled(false);
		Pumpctrl.end_button.setEnabled(false);
		
		// prompt to flush line
		while ( flush_a == JOptionPane.CANCEL_OPTION) {
			try {
				flush_a = JOptionPane.showOptionDialog(Pumpctrl.main_frame, flush_q1, "Ready to flush the line?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, flush_options, null);
			} catch (Exception e){}
		}
		
		// JOptionPane.showMessageDialog(Pumpctrl.main_frame,"flush_a = " + flush_a);
		// flush line
		if ( flush_a == JOptionPane.YES_OPTION ) {
			flush_a = JOptionPane.CANCEL_OPTION;
			String flush_q2 = "When you are ready to stop flushing, click OK";
			Pumpctrl.pump_write("rat 10 mm\r",true);
			Pumpctrl.pump_write("run\r",true);
			Pumpctrl.pump_status_label.setText("Pump status: changing syringes, flushing line");
			while ( flush_a == JOptionPane.CANCEL_OPTION) {
				try {
					flush_a = JOptionPane.showOptionDialog(Pumpctrl.main_frame, flush_q2, "Ready to stop flushing?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);
				} catch (Exception e){}
			}
			Pumpctrl.pump_write("stp\r",true);
		}

		flush_a = JOptionPane.CANCEL_OPTION;
		String flush_q3 = "When the stopcock is turned closed-to-air and you are ready to resume the infusion, click OK";
		while ( flush_a == JOptionPane.CANCEL_OPTION ) {
			try {
				flush_a = JOptionPane.showOptionDialog(Pumpctrl.main_frame, flush_q3, "Ready to resume infusing?", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE, null, null, null);
			} catch (Exception e){}
			// catch_up_Int = (Integer) resume_a;
			// catch_up_time = Integer.parseInt(resume_a);
			catch_up_time = 120;
		}
		
		// JOptionPane.showMessageDialog(Pumpctrl.main_frame,"catch_up_time = " + catch_up_time);
		
		// calculate new catch-up rate and start catch-up
		change_time2 = Pumpctrl.infuse_time_panel.sec_elapsed;
		catch_up_rate = (Pumpctrl.vol_delivered(change_time2.intValue() + catch_up_time) - Pumpctrl.vol_delivered(change_time1.intValue()))/(catch_up_time/60);
		Pumpctrl.pump_write("mod pmp\r",false);
		Pumpctrl.pump_write("mod\r",false);
		Pumpctrl.pump_write("rat " + catch_up_rate.toString().substring(0,6) + " mm\r",true);
		Pumpctrl.pump_write("run\r",true);
		Pumpctrl.pump_status_panel.setBackground(Color.GREEN);
		Pumpctrl.pump_status_label.setText("Pump status: infusing (catch-up rate)");
		change_time2 = change_time2 + catch_up_time;
		while ( ! change_time2.equals(change_time1) ) {
			try {
				this.sleep(100);
			} catch (Exception e) {}
			change_time1 = Pumpctrl.infuse_time_panel.sec_elapsed;
		}
		// rejoin normal rate
		Pumpctrl.pump_write("rat " + Pumpctrl.rate[Pumpctrl.rate_indx - 1].toString().substring(0,6) + " mm\r",true);
		Pumpctrl.infuse_go = true;
		Pumpctrl.pump_status_label.setText("Pump status: infusing");
		Pumpctrl.change_button.setEnabled(true);
		Pumpctrl.end_button.setEnabled(true);
	}
}

class Timestamp {
        int hrs;
        int mins;
        int secs;

        void fillTimes() {
            Calendar now;
            now = Calendar.getInstance();
            hrs = now.get(Calendar.HOUR_OF_DAY);
            mins = now.get(Calendar.MINUTE);
            secs = now.get(Calendar.SECOND);
        }
}

class ClockView extends JPanel implements Runnable {

    private JLabel tLabel = new JLabel();
	static Font bigFont = new Font("arial", Font.BOLD, 18);

    ClockView() {
        this.setPreferredSize(new Dimension(220,40));
        this.add(tLabel);
		tLabel.setFont(bigFont);
        this.refreshTimeDisplay();
    }

    protected String getDigitsAsString(int i) {
        String str = Integer.toString(i);
        if (i<10) return "0"+str;
        return str;
    }

    public void refreshTimeDisplay() {
        Timestamp t = new Timestamp();
        t.fillTimes();
        String display = "Clock Time: " + getDigitsAsString(t.hrs) + ":"
                     + getDigitsAsString(t.mins)  + ":"
                     + getDigitsAsString(t.secs);
        tLabel.setText("  " + display );
        tLabel.repaint();
    }

    public void run() {
	    for (;;) {
		    this.refreshTimeDisplay();
		    try {
		    	Thread.sleep(500);
		    } catch (Exception e) {}
	    }
    }
}

class StopWatch extends JPanel implements Runnable {
	Integer sec_elapsed = new Integer(0);
	Calendar time1;
	Calendar time2;
	Calendar timer = Calendar.getInstance();
	boolean run = true;
	JLabel tLabel = new JLabel();
	static Font bigFont = new Font("arial", Font.BOLD, 26);
	
	StopWatch() {
		this.setPreferredSize(new Dimension(440,40));
        this.add(tLabel);
		tLabel.setFont(bigFont);
		this.refreshTimeDisplay();
	}
	
	public void reset() {
		timer.set(Calendar.HOUR_OF_DAY,0);
		timer.set(Calendar.MINUTE,0);
		timer.set(Calendar.SECOND,0);
		time1 = Calendar.getInstance();
		this.refreshTimeDisplay();
	}
		
	public void run() {
		time2 = Calendar.getInstance();
		time2.add(Calendar.SECOND,1);
		time2.add(Calendar.MILLISECOND,-1);
		timer_loop: for (;;) {
			time1 = Calendar.getInstance();
			try {
				Thread.sleep(100);
			} catch (Exception e) {}
			if ( time1.after(time2) && run ) {
				time2.add(Calendar.SECOND,1);
				timer.add(Calendar.SECOND,1);
				sec_elapsed = sec_elapsed + 1;
			}
			this.refreshTimeDisplay();
			if ( ! run ) break timer_loop;
		}
	}

	public void pauseWatch() {
		run = false;
	}

	protected String getDigitsAsString(int i) {
        	String str = Integer.toString(i);
        	if (i<10) return "0"+str;
        	return str;
    	}
	
	public String getTimeAsString() {
		return getDigitsAsString(timer.get(Calendar.HOUR_OF_DAY)) + ":"
                     + getDigitsAsString(timer.get(Calendar.MINUTE))  + ":"
                     + getDigitsAsString(timer.get(Calendar.SECOND));
	}

	public void refreshTimeDisplay() {
        	String display = "Infusion Running Time:  " + getTimeAsString();
       		tLabel.setText( display );
        	tLabel.repaint();
    	}
}
		
