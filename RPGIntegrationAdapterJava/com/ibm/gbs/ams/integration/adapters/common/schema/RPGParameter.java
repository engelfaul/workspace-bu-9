/**
 * 
 */
package com.ibm.gbs.ams.integration.adapters.common.schema;

/**
 * @author Giovanni Martinez
 *
 */
public class RPGParameter {
	
	//Variables
	private String name;
	/**
	 * @param name
	 * @param type
	 * @param order
	 * @param value
	 */
	public RPGParameter(String name, String type, Integer order, String value) {
		super();
		this.name = name;
		this.type = type;
		this.order = order;
		this.value = value;
	}
	private String type;
	private Integer order;
	private String value;
	
	//Getters and Setters
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Integer getOrder() {
		return order;
	}
	public void setOrder(Integer order) {
		this.order = order;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}

}
