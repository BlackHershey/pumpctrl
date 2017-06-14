import javax.swing.*;

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
