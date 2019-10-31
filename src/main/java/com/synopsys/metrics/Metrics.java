package com.synopsys.metrics;

/**
 * A Metrics is a threshold on a measure...
 */
public class Metrics {

	/** What type (given by name) of Measurable object this metrics applies to. */
	public String scope = null;
	
  /** The name of the measure collected on the measured items. */
  public String metric = null;

  /** The name */
  public String name = null;

  /** The threshold above which a defect should be triggered. */
  public double value = 0;
}
