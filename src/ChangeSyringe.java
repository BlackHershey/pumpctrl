import javax.swing.*;
import java.awt.*;

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
