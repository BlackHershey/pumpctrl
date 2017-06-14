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