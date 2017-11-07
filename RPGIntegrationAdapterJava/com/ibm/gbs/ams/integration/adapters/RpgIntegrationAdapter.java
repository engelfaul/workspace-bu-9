package com.ibm.gbs.ams.integration.adapters;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbBLOB;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbJavaException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.gbs.ams.integration.adapters.common.schema.RPGParameter;
import com.ibm.gbs.ams.integration.adapters.rpg.CallRPGProgram;

public class RpgIntegrationAdapter extends MbJavaComputeNode {

	/** Constants **/
	private static final String ELEM_VARIABLES = "Variables";
	private static final String ELEM_MSGREQUEST = "MsgRequest";
	private static final String ELEM_HEADER = "Header";
	private static final String ELEM_MSGID = "messageId";
	private static final String ELEM_TRANS_REQUEST = "TransactionRequest";
	private static final String XPATH_PARAMETERS = "*[contains(name(),'Parameters')]";
	private static final String PARAMETER_TYPE_IN = "IN";
	private static final String PARAMETER_TYPE_OUT = "OUT";
	private static final String TRACE_TYPE_REQUEST = "REQUEST";
	private static final String TRACE_TYPE_RESPONSE = "RESPONSE";

	public void evaluate(MbMessageAssembly inAssembly) throws MbJavaException,
	MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbMessage inMessage = inAssembly.getMessage();
		MbMessage outMessage = new MbMessage();
		MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly,
				outMessage);
		List<RPGParameter> listParameters = null;
		CallRPGProgram callRPGProgram = null;
		List<RPGParameter> respBEParameters = null;
		// Copiar los headers.
		this.copyMessageHeaders(inMessage, outMessage);

		try {
			// Construir Mensaje de request.
			listParameters = this.buildRPGParameters(inMessage);
			if (listParameters != null) {

				// hacer traza antes de llamar al backend
				this.makeTrace(outAssembly, inMessage, outMessage,
						listParameters, TRACE_TYPE_REQUEST);
				callRPGProgram = new CallRPGProgram();
				// invocar el programa RPG
				//respBEParameters = callRPGProgram.callRPGprogram(inMessage,
				//		listParameters);
				//Este codigo es para obtener un mensaje a traves de un Dummy
				respBEParameters = callRPGProgram.callRPGprogramDummy(inMessage,
						listParameters);
				// hacer traza de la respuesta del backEnd
				this.makeTrace(outAssembly, inMessage, outMessage,
						listParameters, TRACE_TYPE_RESPONSE);
				if (respBEParameters != null) {
					this.generateResponseXML(inMessage, outMessage, respBEParameters);
				}
				out.propagate(outAssembly);
			}
		}
		// Manejo de errores controlados al ejecutar request en el servidor
		// iSeries

		catch (IseriesInvocationException e) {
			throw new MbUserException(RpgIntegrationAdapter.class.getName(),
					"RpgIntegrationAdapter.evaluate", "", "", e.getMessage(),
					null);
		}
		// Manejo de otro tipo de excepciones no controladas
		catch (Exception e) {
			throw new MbUserException(RpgIntegrationAdapter.class.getName(),
					"RpgIntegrationAdapter.evaluate", "", "", e.getMessage(),
					null);
		}
		finally {

			outMessage.clearMessage();
		}
	}

	public void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage)
			throws MbException {
		MbElement outRoot = outMessage.getRootElement();

		// iterate though the headers starting with the first child of the root
		// element
		MbElement header = inMessage.getRootElement().getFirstChild();
		while (header != null && header.getNextSibling() != null) // stop before
		{
			// copy the header and add it to the out message
			outRoot.addAsLastChild(header.copy());
			// move along to next header
			header = header.getNextSibling();
		}
	}

	public String get_UDP_Broker(String s_udp) throws MbException {
		String ls_udp_valor = "";
		Object obj_udp = null;
		obj_udp = getUserDefinedAttribute(s_udp);
		if (obj_udp != null) {
			ls_udp_valor = obj_udp.toString();
		}
		return ls_udp_valor;
	}

	/**
	 * @param inMessage
	 * @return
	 * @throws MbException
	 */
	private List<RPGParameter> buildRPGParameters(MbMessage inMessage)
			throws MbException {
		List<RPGParameter> listParameters = new ArrayList<RPGParameter>(0);
		RPGParameter rpgParameter = null;
		StringBuilder msgRequestBd = null;
		int orderParameters = 0;
		// Variable utilizada para obtener elementos
		MbElement commodity_ = null;
		// obtener información del mensaje de entrada
		MbElement inputBody = inMessage.getRootElement().getLastChild()
				.getFirstElementByPath(ELEM_TRANS_REQUEST);
		MbElement outCardinality = inputBody
				.getFirstElementByPath("outputCardinality");
		MbElement responseIndex = inputBody
				.getFirstElementByPath("responseIndex");

		MbElement validationTagIndexStrMb = inputBody
				.getFirstElementByPath("validationTagIndex");

		int validationTagIndex = -1;

		if ((validationTagIndexStrMb != null)) {
			validationTagIndex = Integer.parseInt(validationTagIndexStrMb
					.getValueAsString());
		}

		MbElement validationValueMb = inputBody
				.getFirstElementByPath("validationValue");
		String validationValue = "";
		if (validationValueMb != null) {
			validationValue = validationValueMb.getValueAsString();
		}
		boolean outputCardinality = false;
		ArrayList<String> parameters = new ArrayList<String>();
		String cardinality = null;
		int cont = 0;

		if (outCardinality != null) {
			outputCardinality = Boolean.valueOf(outCardinality
					.getValueAsString());
		}
		int responseIndexValue = 0;
		if (responseIndex != null)
			responseIndexValue = Integer.parseInt(responseIndex
					.getValueAsString()) - 1;

		// arbol de parametros de entrada

		// Construir objeto RPGParameter
		@SuppressWarnings("unchecked")
		List<MbElement> allParameters = (List<MbElement>) inputBody
		.evaluateXPath(XPATH_PARAMETERS);

		for (MbElement parameter : allParameters) {
			msgRequestBd = new StringBuilder();
			// validar si el parametro es de IN o OUT
			if (parameter.getName().startsWith("Input")) {
				// iterar sobre la lista de parametros de entrada
				@SuppressWarnings("unchecked")
				List<MbElement> parametersList = (List<MbElement>) parameter
				.evaluateXPath("Parameter");
				Iterator<MbElement> itParameters = parametersList
						.listIterator();

				while (itParameters.hasNext()) {
					MbElement inputparam_ = itParameters.next();
					commodity_ = inputparam_.getFirstElementByPath("value");
					msgRequestBd.append(commodity_.getValueAsString());
				}
				rpgParameter = new RPGParameter(parameter.getName(),
						PARAMETER_TYPE_IN, orderParameters,
						msgRequestBd.toString());
				listParameters.add(rpgParameter);
				orderParameters++;
			} else {
				rpgParameter = new RPGParameter(parameter.getName(),
						PARAMETER_TYPE_OUT, orderParameters, " ");
				listParameters.add(rpgParameter);
				orderParameters++;
			}
		}
		return listParameters;
	}

	/**
	 * @param listParams
	 * @return
	 */
	private String concatListValueParams(List<String> listParams) {
		StringBuilder sbResult = new StringBuilder();
		if (listParams != null) {
			for (String param : listParams) {
				sbResult.append(param);
			}
		}
		return sbResult.toString();
	}

	private void makeTrace(MbMessageAssembly outAssembly, MbMessage inMessage,
			MbMessage outMessage, List<RPGParameter> listParameters,
			String traceType) throws MbException {

		List<String> listParametersConcat = new ArrayList<String>(0);
		// validar los parametros a realizar trace (IN/OUT).
		if (TRACE_TYPE_REQUEST.equals(traceType)) {
			// asignar los parametros a realizar trace, dependiendo si es Req o
			// Resp.
			for (RPGParameter rpgParametersTrace : listParameters) {
				if (PARAMETER_TYPE_IN.equals(rpgParametersTrace.getType())) {
					listParametersConcat.add(rpgParametersTrace.getValue());
				}
			}
		} else {
			// asignar los parametros a realizar trace, dependiendo si es Req o
			// Resp.
			for (RPGParameter rpgParametersTrace : listParameters) {
				if (PARAMETER_TYPE_OUT.equals(rpgParametersTrace.getType())) {
					listParametersConcat.add(rpgParametersTrace.getValue());
				}
			}
		}

		// construcción del mensaje para traza
		MbElement outputRoot = outMessage.getRootElement();
		MbElement outputBody = outputRoot
				.createElementAsLastChild(MbBLOB.PARSER_NAME);

		MbElement blobData = outputBody.createElementAsFirstChild(
				MbElement.TYPE_NAME_VALUE, "BLOB",
				this.concatListValueParams(listParametersConcat).getBytes());
		// obtener información del mensaje de entrada
		MbElement inputBody = inMessage.getRootElement().getLastChild()
				.getFirstElementByPath(ELEM_TRANS_REQUEST);
		// obetener msg del mensaje de entrada.
		String msgId = inputBody.getFirstElementByPath(ELEM_MSGID)
				.getValueAsString();

		// COnstruir Environment.Variables.MsgRequest.Header.messageId
		// Se obtienen las variables del Environment
		MbMessage environmentMessage = outAssembly.getGlobalEnvironment();
		MbElement variables = environmentMessage.getRootElement()
				.createElementAsLastChild(MbElement.TYPE_NAME);
		variables.setName(ELEM_VARIABLES);
		MbElement envMsgReq = variables
				.createElementAsLastChild(MbElement.TYPE_NAME);
		envMsgReq.setName(ELEM_MSGREQUEST);
		MbElement envHeader = envMsgReq
				.createElementAsLastChild(MbElement.TYPE_NAME);
		envHeader.setName(ELEM_HEADER);
		envHeader.createElementAsLastChild(MbElement.TYPE_NAME, ELEM_MSGID,
				msgId);
		// Se propaga el mensaje directamente a la salida
		MbOutputTerminal alternate = getOutputTerminal("alternate");
		alternate.propagate(outAssembly);
		// limpiamos la salida.
		blobData.delete();
		outputBody.delete();

	}

	private static boolean validarResponse(String response_,
			int validationTagIndex, String validationValue) {

		String value = response_.substring(validationTagIndex - 1,
				validationValue.length());
		return value.equals(validationValue);
	}
	

	private MbMessage generateResponseXML(MbMessage inMessage,
			MbMessage outMessage, List<RPGParameter> rpgParameters)
					throws MbException {

		// Variable utilizada para obtener los parametros de configuración
		MbElement commodity_ = null;
		// Declaración Global del mensaje de respuesta
		MbElement mbeXMLOutput = null;

		// obtener información del mensaje de entrada
		MbElement inputBody = inMessage.getRootElement().getLastChild()
				.getFirstElementByPath("TransactionRequest");

		MbElement validationValueMb = inputBody
				.getFirstElementByPath("validationValue");
		String validationValue = "";
		if (validationValueMb != null) {
			validationValue = validationValueMb.getValueAsString();
		}

		// construcción del mensaje de respuesta
		mbeXMLOutput = outMessage.getRootElement().createElementAsLastChild(
				"XMLNSC");

		MbElement mbeTxResponse = mbeXMLOutput.createElementAsLastChild(
				MbElement.TYPE_NAME, "TransactionResponse", "");
		MbElement mbeStandard = mbeTxResponse.createElementAsLastChild(
				MbElement.TYPE_NAME, "Standard", "");
		//Crear el elemento Header
		MbElement mbeHeader = mbeStandard.createElementAsLastChild(
				MbElement.TYPE_NAME, ELEM_HEADER, "");		
		//Crear el elemento Body
		MbElement mbeBody = mbeStandard.createElementAsLastChild(
				MbElement.TYPE_NAME, "Body", "");			
		MbElement mbeRegisters = mbeBody.createElementAsLastChild(
				MbElement.TYPE_NAME, "Registers", "");
		String standardresponse1_ = "";
		// Obtener los parametros de salida para parsear la trama.
		for (RPGParameter rpgParameterOut : rpgParameters) {
			if (rpgParameterOut.getType().equals("OUT")) {

				MbElement outputParameters = inputBody
						.getFirstElementByPath(rpgParameterOut.getName());

				//Obtener el padre del pa estructura de parametros de salida
				MbElement mbeParent = outputParameters
						.getFirstElementByPath("parent");

				// Configuración del mensaje
				MbElement standardParameters = outputParameters
						.getFirstElementByPath("StandardParameters");
				@SuppressWarnings("unchecked")
				List<MbElement> ParameterList = (List<MbElement>) standardParameters
				.evaluateXPath("Parameter");
				@SuppressWarnings("unchecked")
				List<MbElement> Longitud = (List<MbElement>) standardParameters
				.evaluateXPath("long");

				int longitud_ = Integer.parseInt(Longitud.get(0)
						.getValueAsString());

				// Clasificar la trama de respuesta según archivo de
				// configuración
				//String response_ = "B00000000OK                                                                                                                                                                                                                                                             ";
				String response_ = "";
				response_ = rpgParameterOut.getValue();

				String standardresponse_ = response_.substring(0, longitud_);
				String repetitiveresponse_ = response_.substring(longitud_);

				// Construcción Arbol de respuesta estandar
				Iterator<MbElement> paramoutputlist = ParameterList
						.listIterator();
				int taglong_ = 0;
				
				if(rpgParameterOut.getName().equals("OutputParameters2")){
					standardresponse1_ = rpgParameterOut.getValue();
				}
				// Iteración sobre la lista de parametros de
				// salida
				while (paramoutputlist.hasNext()) {
					MbElement outputparam_ = paramoutputlist.next();
					commodity_ = outputparam_.getFirstElementByPath("name");
					String nombreparametro_ = commodity_.getValueAsString();

					commodity_ = outputparam_.getFirstElementByPath("long");
					int long_ = Integer.parseInt(commodity_
							.getValueAsString());
					//validar si el padre es Header o Body
					if (ELEM_HEADER.equals(mbeParent.getValueAsString())){
						mbeHeader.createElementAsLastChild(
								MbElement.TYPE_NAME,
								nombreparametro_,
								standardresponse_.substring(taglong_, taglong_
										+ long_));						
					}else{
						mbeRegisters.createElementBefore(
								MbElement.TYPE_NAME,
								nombreparametro_,
								standardresponse_.substring(taglong_, taglong_
										+ long_));						
					}
					taglong_ = taglong_ + long_;
				}

				MbElement repetitiveParameters = outputParameters
						.getFirstElementByPath("RepetitiveParameters");
				if (repetitiveParameters != null) {

					ParameterList = (List<MbElement>) repetitiveParameters
							.evaluateXPath("Parameter");

					@SuppressWarnings("unchecked")
					List<MbElement> Token = (List<MbElement>) repetitiveParameters
					.evaluateXPath("token");

					if (!Token.isEmpty()) {

						// Obtener el caracter del token
						byte[] buffer_ = { (byte) Integer.parseInt(Token
								.get(0).getValueAsString()) };
						String token_ = new String(buffer_);

						// Construcción árbol de respuesta
						// repetitiva
						while (repetitiveresponse_.indexOf(token_) > 0) {
							Iterator<MbElement> itoutputlist = ParameterList
									.listIterator();
							MbElement mbeParameters = mbeRegisters
									.createElementAsLastChild(
											MbElement.TYPE_NAME,
											"Register", "");
							// Iteración sobre la lista de
							// parametros de
							// salida
							while (itoutputlist.hasNext()) {
								MbElement outputparam_ = itoutputlist
										.next();
								@SuppressWarnings("unchecked")
								List<MbElement> name_ = (List<MbElement>) outputparam_
								.evaluateXPath("name");
								String nombreparametro_ = name_.get(0)
										.getValueAsString();
								String valor_ = repetitiveresponse_
										.substring(0, repetitiveresponse_
												.indexOf(token_));
								repetitiveresponse_ = repetitiveresponse_
										.substring(repetitiveresponse_
												.indexOf(token_) + 1);
								mbeParameters.createElementAsLastChild(
										MbElement.TYPE_NAME,
										nombreparametro_, valor_);
							}
						}
					} else {

						// Posición de "CantidadRegistros"
						@SuppressWarnings("unchecked")
						List<MbElement> MbsPosCantRep = (List<MbElement>) repetitiveParameters
						.evaluateXPath("repPos");
						int positionCantRep = Integer
								.parseInt(MbsPosCantRep.get(0)
										.getValueAsString());

						// Longitud de "CantidadRegistros"
						@SuppressWarnings("unchecked")
						List<MbElement> MbsLenCantRep = (List<MbElement>) repetitiveParameters
						.evaluateXPath("repLon");
						int lengthCantRep = Integer.parseInt(MbsLenCantRep
								.get(0).getValueAsString());


						if(!standardresponse1_.isEmpty()){
							int cantidadRegistros = Integer
									.parseInt(standardresponse1_
											.substring(positionCantRep - 1,
													positionCantRep - 1
													+ lengthCantRep));

							// No se puede leer con Token .. se debe
							// leer
							// con Longitud
							int countIterativefield = cantidadRegistros;
							int taglong_rep = 0;
							for (int i = 0; i < countIterativefield; i++) {

								MbElement mbeParameters = mbeRegisters
										.createElementAsLastChild(
												MbElement.TYPE_NAME,
												"Register", "");

								for (MbElement mbParamList : ParameterList) {

									String nombreparametro_ = mbParamList
											.getFirstElementByPath("name")
											.getValueAsString();
									int long_ = Integer.parseInt(mbParamList
											.getFirstElementByPath("long")
											.getValueAsString());
									mbeParameters.createElementAsLastChild(
											MbElement.TYPE_NAME,
											nombreparametro_,
											repetitiveresponse_.substring(
													taglong_rep, taglong_rep
													+ long_));
									taglong_rep += long_;
								}
							}
						}

					}
				}

			}
		}

		return outMessage;

	}

}
