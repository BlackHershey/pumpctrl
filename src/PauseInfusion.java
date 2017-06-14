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