import java.util.*;
import java.awt.*;

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
		if ( Pumpctrl.infusion_method.equals("DR") ){
			Pumpctrl.log_stream.println("Rate change interval (sec): " + Pumpctrl.d_int.toString());
		} else {
			Pumpctrl.log_stream.println("Loading dose length (min): " + Pumpctrl.d_int.toString());
		}
		Pumpctrl.log_stream.println("Total infusion time (min): " + (Pumpctrl.total_time/60));
		Pumpctrl.log_stream.println("");
		Pumpctrl.log_stream.print("Target volume to be infused (if infusion runs for " + (Pumpctrl.total_time/60) + " min) = ");
		Pumpctrl.log_stream.println(Pumpctrl.vol_delivered(Pumpctrl.total_time.intValue()));
		c1 = start_time;
		if ( Pumpctrl.infusion_method.equals("DR") ){
			c1.add(Calendar.SECOND,Pumpctrl.d_int.intValue());
		}	else {
			c1.add(Calendar.MINUTE,Pumpctrl.d_int.intValue());
		}
		c1.add(Calendar.MILLISECOND,-1);
		Pumpctrl.running_time = Pumpctrl.running_time + Pumpctrl.d_int;
		Pumpctrl.rate_indx++;
		Pumpctrl.infuse_go = true;
		Pumpctrl.infuse_timer = true;
		Pumpctrl.pump_status_panel.setBackground(Color.GREEN);
		if ( Pumpctrl.infusion_method.equals("DR") ){
			Pumpctrl.pump_status_label.setText("Pump status: infusing");
		} else {
			Pumpctrl.pump_status_label.setText("Pump status: infusing loading dose");
		}
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
							if ( Pumpctrl.infusion_method.equals("LM") ){
								Pumpctrl.pump_status_label.setText("Pump status: maintenance infusion");
							}
							Pumpctrl.pump_write("del\r",true);
							break;
						}
					}
				}
				Pumpctrl.infuse_progress.setValue(Pumpctrl.running_time.intValue());
				Pumpctrl.running_time = Pumpctrl.running_time + Pumpctrl.d_int;
				Pumpctrl.rate_indx++;
				Pumpctrl.io_scroll.getVerticalScrollBar().setValue(100);
				if ( Pumpctrl.infusion_method.equals("DR") ){
					c1.add(Calendar.SECOND,Pumpctrl.d_int.intValue());
				} else {
					c1.add(Calendar.SECOND,Pumpctrl.total_time.intValue() - Pumpctrl.d_int.intValue()*60);
					break;
				}
			}
		}

		// while loop for maintenance rate
		if ( Pumpctrl.infusion_method.equals("LM") ){
			while ( Pumpctrl.running_time <= Pumpctrl.total_time && Pumpctrl.infusion_running) {
				try { Thread.sleep(100); } catch (Exception c) {}
				if ( Pumpctrl.infuse_time_panel.time1.after(c1)) {
					break;
				}
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
                     + getDigitsAsString(cal.get(Calendar.MINUTE)) + ":"
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
