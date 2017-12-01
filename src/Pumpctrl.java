import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.util.*;
import javax.comm.*;
import static java.lang.Math.*;

// Pumpctrl class holds the GUI and talks to the serial port
public class Pumpctrl extends Thread implements SerialPortEventListener {

	// COM port variables
	static Enumeration portList;
	static CommPortIdentifier portId;
	static SerialPort serialPort;

	// log file variables
	static OutputStream outputStream;
	static InputStream inputStream;

	// main window containers, buttons, labels, etc
	static JFrame main_frame = new JFrame("Pump Control version 1.1.7-20171030");
	static ClockView clock_panel = new ClockView();
	static StopWatch infuse_time_panel = new StopWatch();
	static JLabel infuse_start = new JLabel("      Infusion Start Time:  00:00:00");
	static JPanel pump_status_panel = new JPanel();
	static JLabel pump_status_label = new JLabel();
	static JLabel pump_program_version_label = new JLabel("program version 1.1.7");
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
	static int exit_button;
	static int end_button_press;

	// decreasing rate infusion constants and variables
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

	// loading + maintenance infusion constants and variables
	static double LM_loading_target_ratio = 0.6426; // mg/kg
	static double LM_maintenance_ratio = 2.882; // mg/kg/min
	static Double loading_dose_rate = new Double(0);
	static Double maintenance_rate = new Double(0);


	// timing constants
	static Double d_int = new Double(0);
	static Double d_int_default = new Double(60.0);
	static Double total_time = new Double(0);
	static Double total_time_default = new Double(180.0);
	static Double running_time = new Double(0);
	static int rate_num = 0;
	static int rate_indx = 0;

	// setup info variables
	static String infusion_method;
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

	// font specification
	static Font bigFont = new Font("arial", Font.BOLD, 18);

	// function that handles incoming serial port data
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

	// set up the main GUI window
	public void setup_main_window(){

		// set up main frame
		main_frame.setSize(1000,800);
		main_frame.setResizable(false);
		main_frame.getContentPane().setLayout( new FlowLayout(FlowLayout.CENTER,10,10) );
		main_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		// listener and function to handle the window exit button
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

		// set up border for panels
		Border etched_border;
		etched_border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);

		// set up upper left panel
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

		// listener to call setup function
		ActionListener b = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				setup();
			}
		};
		setup_button.addActionListener(b);
		setup_button.setToolTipText("Press to do setup steps");
		ctrl_panel.add(setup_button);

		// listener for infusion "start" button
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

		// listener for "bathroom break" button
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

		// listener for "change syringe" button
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

		// listener for serial port "connect" button
		ActionListener h = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				establish_com();
			}
		};
		connect_button.setEnabled(true);
		connect_button.addActionListener(h);
		connect_button.setToolTipText("Press to connect to pump");
		ctrl_panel.add(connect_button);

		// listener for "add time" button
		ActionListener i = new ActionListener(){
			public void actionPerformed(ActionEvent e){
				add_time();
			}
		};
		add_time_button.setEnabled(false);
		add_time_button.addActionListener(i);
		add_time_button.setToolTipText("Press to add time to the end of the infusion");
		// ctrl_panel.add(add_time_button);

		// listener for "end infusion" button
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

		// size and position for control panel and output scroll panel
		ctrl_panel.setPreferredSize(new Dimension(750,41));
		ctrl_panel.setBorder(etched_border);
		main_frame.add(ctrl_panel);

		io_scroll = new JScrollPane(serial_io);
		io_scroll.setPreferredSize(new Dimension(750,400));
		io_scroll.setWheelScrollingEnabled(true);
		main_frame.add(io_scroll);

		// pump_program_version_label.setFont(new Font("Dialog", Font.BOLD, 8));
		// main_frame.add(pump_program_version_label);

		// open main frame
		main_frame.setVisible(true);
	}

	// setup function (get subject info, infusion type, settings)
	public static void setup(){
		JLabel q0 = new JLabel("Choose an infusion method: ");
		JLabel q1 = new JLabel("Enter subject ID: ");
		JLabel q2 = new JLabel("Enter visit day: ");
		JLabel q3 = new JLabel("Enter subject's age(yrs): ");
		JLabel q4 = new JLabel("Enter subject's weight(lbs): ");
		JLabel q6 = new JLabel("Enter the time interval for the pump rate change(sec): ");
		JLabel q7 = new JLabel("Enter the total time of the scan(min): ");
		JLabel q8 = new JLabel("Enter the diameter of the pump syringe(mm): ");
		JLabel q9 = new JLabel("Enter the target plasma concentration (ng/mL): ");
		String title0 = "Choose infusion method";
		String title1 = "Enter subject ID";
		String title2 = "Enter visit day";
		String title3 = "Enter age";
		String title4 = "Enter weight";
		String title6 = "Enter rate change interval";
		String title7 = "Enter total time";
		String title8 = "Enter syringe diameter";
		String title9 = "Enter target plasma conc.";
		int a0 = 0;
		String a1 = new String();
		String a2 = new String();
		String a3 = new String();
		String a4 = new String();
		Object a6 = new Object();
		Object a7 = new Object();
		Object a8 = new Object();
		Object a9 = new Object();
		int a10 = 0;

		// infusion type radio buttons
    JRadioButton loadmaintButton = new JRadioButton("loading + maintenance");
    loadmaintButton.setMnemonic(KeyEvent.VK_L);
    loadmaintButton.setActionCommand("LM");
    loadmaintButton.setSelected(false);
    JRadioButton decreasingrateButton = new JRadioButton("decreasing rate");
    decreasingrateButton.setMnemonic(KeyEvent.VK_D);
    decreasingrateButton.setActionCommand("DR");
		decreasingrateButton.setSelected(false);
    ButtonGroup infuse_method_button_group = new ButtonGroup();
    infuse_method_button_group.add(loadmaintButton);
    infuse_method_button_group.add(decreasingrateButton);
		JPanel infusion_method_choices_panel = new JPanel();
		infusion_method_choices_panel.add(q0);
		infusion_method_choices_panel.add(loadmaintButton);
		infusion_method_choices_panel.add(decreasingrateButton);

		// ask setup questions
		while( true ){
			a0 = JOptionPane.showOptionDialog(main_frame,infusion_method_choices_panel,title0,JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE,null,null,null);
			try {
				infusion_method = new String(infuse_method_button_group.getSelection().getActionCommand());
			} catch (Exception e) {}
			if ( infusion_method.length() > 1 ) break;
		}

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
			if ( age > 17 && age < 100 ) break;
		}

		while( true ){
			a4 = JOptionPane.showInputDialog(main_frame,q4,title4,JOptionPane.QUESTION_MESSAGE);
			try {
				weight = weight.valueOf(a4);
			} catch (Exception e) {}
			if ( weight > 80 && weight < 300 ) break;
		}

		if ( infusion_method.equals("LM") ){
			System.out.println("infusion method = " + infusion_method);
			q6 = new JLabel("Enter the loading dose length (min): ");
			q7 = new JLabel("Enter the total infusion length (min): ");
			title6 = "Enter loading dose length";
			title7 = "Enter total infusion length";
			d_int_default = 10.0;
			total_time_default = 130.0;
		}

		while( true ){
			a6 = JOptionPane.showInputDialog(main_frame,q6,title6,JOptionPane.QUESTION_MESSAGE,null,null,d_int_default);
			try {
				d_int = Double.parseDouble(a6.toString());
			} catch (Exception e) {}
			if ( d_int >= 5 && d_int <= 120 ) break;
		}

		while( true ){
			a7 = JOptionPane.showInputDialog(main_frame,q7,title7,JOptionPane.QUESTION_MESSAGE,null,null,total_time_default);
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

		if ( infusion_method.equals("DR") ){
			while( true ){
				a9 = JOptionPane.showInputDialog(main_frame,q9,title9,JOptionPane.QUESTION_MESSAGE,null,null,1200.0);
				try {
					target_plasma = Double.parseDouble(a9.toString());
				} catch (Exception e) {}
				if ( target_plasma > 100 && target_plasma <= 1200 ) break;
			}
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
		mass = weight/2.205;

		// use answers to setup questions to set up infusion
		subject_table.setValueAt("Age:",0,0);
		subject_table.setValueAt("Weight:",1,0);
		subject_table.setValueAt(age.toString() + " yrs",0,1);
		subject_table.setValueAt(weight.toString() + " lbs",1,1);
		total_time = total_time*60;
		infuse_progress.setMinimum(0);
		infuse_progress.setMaximum(total_time.intValue());
		if ( infusion_method.equals("DR") ){
			subject_table.setValueAt("Target plasma conc.:",2,0);
			subject_table.setValueAt("Target volume:",3,0);
			subject_table.setValueAt("Infusion length:",4,0);
			subject_table.setValueAt("Rate change interval:",5,0);
			subject_table.setValueAt(target_plasma + " ng/mL",2,1);
			subject_table.setValueAt(total_time + " min",4,1);
			subject_table.setValueAt(d_int + " sec",5,1);
			rate_num = new Double((total_time / d_int )).intValue();
			rate = new Double[rate_num];
			vod = 1052 + 0.8172*(age - 21.5);
			clearance = 5.693 + (21.5 - age)*0.04813;
			r_part1 = (target_plasma*mass)/pharmacy;
			r_part2 = (60*JM_load_constant*vod*load_decay_const)/(1200*1078*70*d_int);
			r_check2 = 0.000001*clearance;
			set_rates();
			double vol_display = vol_delivered(total_time.intValue());
			vol_display = vol_display*100;
			long vol_display2 = round(vol_display);
			vol_display = (new Double(vol_display2))/100;
			subject_table.setValueAt(vol_display + " mL",3,1);
		} else {
			subject_table.setValueAt("Loading dose rate:",2,0);
			subject_table.setValueAt("Maintenance infusion rate:",3,0);
			subject_table.setValueAt("Loading dose length:",4,0);
			subject_table.setValueAt("Infusion length:",5,0);
			subject_table.setValueAt(String.format("%.1f", total_time/60.0) + " min",5,1);
			// calculate rates
			loading_dose_rate = ((LM_loading_target_ratio * mass)/d_int)/pharmacy;
			maintenance_rate = ((LM_maintenance_ratio*mass*0.00001)*(140-age))/pharmacy;

			subject_table.setValueAt(String.format("%.4f", loading_dose_rate) + " mL/min",2,1);
			subject_table.setValueAt(String.format("%.4f", maintenance_rate) + " mL/min",3,1);
			subject_table.setValueAt(d_int + " min",4,1);

			rate_num = 2;
			rate = new Double[rate_num];
			rate[0] = loading_dose_rate;
			rate[1] = maintenance_rate;
		}

		// set syringe diameter on pump
		pump_write("dia " + dia.toString() + "\r",true);
		pump_write("dia\r",true);

		// enable "start" button
		infuse_button.setEnabled(true);

	}

	// function to establesh serial port connection to Harvard 44 pump
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

	// function that send commands to the Harvard 44 pump
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

	// function that enters infusion rates into the "rate" array
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

	// fucntion that returns how much should have been delivered by the pump after a given time
	public static double vol_delivered( int sec ) {
		double ans = 0;
		double input_min = 0;
		int i = 0;
		int j = 0;
		int step = d_int.intValue();
		if ( infusion_method.equals("DR") ){
			for ( i = 0; i < sec; i++ ) {
				ans += (rate[j])/60;
				if ( i == step ) {
					step += d_int.intValue();
					j++;
				}
			}
		} else {
			input_min = sec/60.0;
			if ( input_min <= d_int ){
				ans = rate[0] * input_min;
			} else {
				ans = (rate[0] * d_int) + (rate[1] * (input_min - d_int));
			}
		}
		return ans;
	}

	// function that runs when the program thread starts
	public void run() {
		setup_main_window();
	}

	// fucntion for adding time to the infusion (not yet done!)
	public static void add_time() {
		// add some time to the end of the infusion
		time_added = true;
	}

	// main function
	public static void main(String[] args) {
		Pumpctrl pump = new Pumpctrl();
		pump.start();
	}
}
