package com.synopsys.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class CompositeMetrics extends Measurable {

	protected List<Measurable> _measures;
	protected Map<String, StatData> _measuresStat;

	public CompositeMetrics(String name) {
		super(name);
		addMetrics("count",0.0);
		_measures = new ArrayList<>();
		_measuresStat = new HashMap<>();
	}

	@Override
	public Stream<String> getAllSources() {
		return _measures.stream()//
				.flatMap(Measurable::getAllSources)//
				.flatMap(strList -> Stream.of(strList.split(",", 200))).sorted()//
				.distinct();
	}

	public Stream<Measurable> stream() {
		return _measures.stream();
	}

	public void add(Measurable m) {
		_measures.add(m);
		_measuresStat.clear();
	}

	public StatData getMetricStat(String name) {
		StatData stat = _measuresStat.get(name);
		if (stat == null) {
			stat = new StatData();
			for (Measurable m : _measures)
				stat.add(m.getMetric(name));
			_measuresStat.put(name, stat);
		}
		return stat;
	}

	@Override
	public boolean isMetrics(String metricName) {
		if (!super.isMetrics(metricName)) {
			int pos = metricName.lastIndexOf('_');
			if (pos != -1) {
				String prefix = metricName.substring(0, pos);
				String suffix = metricName.substring(pos + 1);
				if (suffix.matches("(min)|(max)|(mean)|(sum)|(count)")) {
					return super.isMetrics(prefix);
				} 
			}
			return false;
		} 
		return true;
	}

	@Override
	public double getMetric(String name) {
		double result = 0.0d;
		if (name.endsWith("_max")) {
			String metric = name.substring(0, name.lastIndexOf("_max"));
			result = getMetricStat(metric).max;
		} else if (name.endsWith("_min")) {
			String metric = name.substring(0, name.lastIndexOf("_min"));
			result = getMetricStat(metric).min;
		} else if (name.endsWith("_mean")) {
			String metric = name.substring(0, name.lastIndexOf("_mean"));
			result = getMetricStat(metric).mean();
		} else if (name.endsWith("_sum")) {
			String metric = name.substring(0, name.lastIndexOf("_sum"));
			result = getMetricStat(metric).sum;
		} else if (name.endsWith("_count")) {
			String metric = name.substring(0, name.lastIndexOf("_count"));
			result = getMetricStat(metric).count;
		} else if (name.equals("count")) {
			result = _measures.size();
		} else {
			result = super.getMetric(name);
		}
		return result;
	}
}
