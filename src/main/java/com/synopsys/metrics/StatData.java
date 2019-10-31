package com.synopsys.metrics;

public class StatData {
	public double min;
	public double max;
	public long count;
	public double sum;

	public StatData() {
		min = max = sum = 0;
		count = 0;
	}
	
	public StatData(double min, double max, long count, double sum) {
		this.min = min;
		this.max = max;
		this.count = count;
		this.sum = sum;
	}
	
	public double mean() {
		return (count > 0) ? sum / (double) count : 0;
	}
	public void add(double v) {
		if (count == 0) {
			min = max = sum = v;
			count = 1;
		} else {
			min = Double.min(min, v);
			max = Double.max(min, v);
			sum += v;
			count++;
		}
	}
}