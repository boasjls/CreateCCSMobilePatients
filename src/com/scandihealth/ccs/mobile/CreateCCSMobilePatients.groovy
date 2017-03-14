package com.scandihealth.ccs.mobile
@Grab('com.github.groovy-wslite:groovy-wslite:1.1.2')
import wslite.soap.*

import groovy.time.TimeCategory

class CreateCCSMobilePatients {

	static main(args) {

		def (environment, printer) = parseCommandLineArguments(args)
		def (url, getIdentURL) = getConfigForEnvironment(environment)

		def patients = createPatientsForEnvironment(environment, url, getIdentURL)

		def config = [
			patients: patients
		]

		serializeConfig(config, printer)
	}

	static parseCommandLineArguments(args) {
		def envLength = args.length
		if (envLength == 0 || envLength > 2 ) {
			println "You must enter one valid environment name (case insensitive, e.g. RND2) as a parameter. Program will stop."
			// the program cannot proceed, as it needs an environment
			System.exit(-1)
		}
		def printer = envLength > 1 ?
				new PrintStream(new File(args[1]))
				: System.out

		String environment = args[0]

		return [environment, printer]
	}

	static getConfigForEnvironment(environment) {
		String url
		String getIdentURL

		if (environment.equalsIgnoreCase("RND2")){
			url = "http://stingray-ccs.scandihealth.com:8004/pam-external-services/services/ccstestsupport"
			getIdentURL = "http://stingray-ccs.scandihealth.com:8004/pam-external-services/services/ccspatientexternal"
		} else if (environment.equalsIgnoreCase("TM11")) {
			url = "http://stingray-ccs.scandihealth.com:8010/pam-external-services/services/ccstestsupport";
			getIdentURL = "http://stingray-ccs.scandihealth.com:8010/pam-external-services/services/ccspatientexternal";
		}
		else if (environment.equalsIgnoreCase("TM17")) {
			url = "http://stingray-ccs.scandihealth.com:8002/pam-external-services/services/ccstestsupport";
			getIdentURL = "http://stingray-ccs.scandihealth.com:8002/pam-external-services/services/ccspatientexternal";
		}
		else if (environment.equalsIgnoreCase("RND1")) {
			url = "http://stingray-ccs.scandihealth.com:8005/pam-external-services/services/ccstestsupport";
			getIdentURL = "http://stingray-ccs.scandihealth.com:8005/pam-external-services/services/ccspatientexternal";
		}
		else if (environment.equalsIgnoreCase("RND3")) {
			url = "http://stingray-ccs.scandihealth.com:8014/pam-external-services/services/ccstestsupport";
			getIdentURL = "http://stingray-ccs.scandihealth.com:8014/pam-external-services/services/ccspatientexternal";
		}
		else {
			println("No supported environment was given as parameter. Contact the developer of this program. Program will stop.");
			// the program cannot proceed, as it needs an environment
			System.exit(-1)
		}

		return [url, getIdentURL]
	}

	static createStandardCPRForAge(age, postfix) {
		use(TimeCategory) {
			def today = new Date()
			def birthday = (today - age).format('ddMMyy')
			return "${birthday}-${postfix}"
		}
	}

	static createPatientsForEnvironment(environment, url, getIdentURL) {

		//Setting up CPR numbers to match the desired ages of test patients
		def localDate = new Date()
		def today = localDate.getCalendarDate().toString() // format example: 2017-02-14T12:28:32.089+0100
		def cprNumbers = use(TimeCategory) {
			[
				patientNewbornId: createStandardCPRForAge(0.days, '6001'),
				patientAgeThreeMonthsId: createStandardCPRForAge(4.months, '6001'),
				patientAgeOneId: createStandardCPRForAge(1.year, '6001'),
				patientAgeFourId: createStandardCPRForAge(4.years, '6001'),
				patientAgeEightId: createStandardCPRForAge(8.years, '6001'),
				patientAgeThirteenId: createStandardCPRForAge(13.years, '6001'),
				adultFemalePatientId: createStandardCPRForAge(20.years, '0002'),
				adultMalePatientId: createStandardCPRForAge(20.years, '0001'),
				seniorMalePatientId: createStandardCPRForAge(71.years, '0001')
			]
		}
		//////////////////////////////////////////
		//Create the patients with specific ages//
		//////////////////////////////////////////
		createPatients(cprNumbers, url, getIdentURL, true, "IMP")

		//////////////////////////////////////////////////////
		// Create the patients with specific encounter types//
		//////////////////////////////////////////////////////
		def outPatient = [cpr: '010100-6001', 'replacement': '010100-6AA1']
		def emergency = [cpr: '010100-6002', replacement: '010100-6AA2']
		def inPatient = [cpr: '010100-6AA3']
		createPatients(outPatient, url, getIdentURL, true, "AMB");
		createPatients(emergency, url, getIdentURL, true, "EMER");
		createPatients(inPatient, url, getIdentURL, true, "IMP");

		//////////////////////////////////////////
		// Create the patient with no encounters//
		//////////////////////////////////////////
		def noEncounter = [cpr: '010100-6003']
		createPatients(noEncounter, url, getIdentURL, false, "")

		def patients = cprNumbers + [
			search: [
				outPatient: outPatient, emergency: emergency,
				inPatient: inPatient, noEncounter: noEncounter,
				contact: '80013812'
			]
		]

		return patients
	}

	private static printIndent(printer, indent) {
		indent.times {
			printer.write("\t")
		}
	}

	private static serializeConfig(nodes, PrintStream printer, indent = 0) {
		nodes.each { key, value ->
			if (value instanceof Map) {
				printIndent(printer, indent)
				printer.println("${key} {")
				serializeConfig(value, printer, indent + 1)
				printIndent(printer, indent)
				printer.println("}")
			} else {
				printIndent(printer, indent)
				printer.println("${key} = '${value}'")
			}
		}
	}

	private static void createPatients(Map<String, String> cprNumbers, String url, String getIdentURL, Boolean createEncounter, String encounterType) {
		for (Map.Entry e in cprNumbers) {
			def cprNumber = e.getValue()
			def CPR = cprNumber.substring(0,6) + cprNumber.substring(7) //ditches the '-' in the CPR number
			createPatientUsingSOAPMessage(CPR, url, getIdentURL, createEncounter, encounterType) //replace with 'CPR' variable later...
		}
	}

	private static void createPatientUsingSOAPMessage(String cpr, String url, String getIdentURL, Boolean createEncounter, String encounterType){
		def identURL = getIdentURL
		def root = ""
		if (!cpr.matches(".*[a-zA-Z].*")) { // if it is a real CPR
			root = "1.3.6.1.4.1.25208.10.20"
		} else { // if it is a replacement CPR
			root = "1.3.6.1.4.1.25208.20.81.20.10.30.2"
		}
		def soapRequest = """
	<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:sch="http://scandihealth.com/ccs/util/schemas" xmlns:sch1="http://scandihealth.com/pam/services/webservice/ccstestsupport/schemas">
		<SOAP-ENV:Header>
			<sch:SecurityInfo>
				<UserId>testuser1</UserId>
				<Password>Testuser-01</Password>
			</sch:SecurityInfo>
		</SOAP-ENV:Header>
		<SOAP-ENV:Body>
			<sch1:CreatePatient>
				<Ident>
					<Root>${root}</Root>
					<Extension>${cpr}</Extension>
				</Ident>
				<FirstName>Allan</FirstName>
				<SurName>Skipper</SurName>
			</sch1:CreatePatient>
		</SOAP-ENV:Body>
	</SOAP-ENV:Envelope>"""
		def client = new SOAPClient(url)
		try {
			def response = client.send(soapRequest)
			println "\nNy patient oprettet med CPR: " + cpr + ", og PAM Id: " + response.CreatePatientResponse.PatientId.text()
			//println "Ny patient: " + response
			//Create encounter for patient
			if (createEncounter){
				def pamId = response.CreatePatientResponse.PatientId.text()
				createEncounterUsingSOAPMessage(pamId, encounterType, url)
			}
		}

		catch (SOAPFaultException e) {
			// If the patient already exists we get a reply with 'PAMPAT00029_UNIQUE_PERSON_IDENT'
			if (e.getMessage().contains("PAMPAT00029_UNIQUE_PERSON_IDENT")){
				//println "Fejl: " + e.toString()
				println "Patient " + cpr + " findes allerede."
				//In case we want an encounter to be created
				if (createEncounter){
					println "Opretter en ny kontakt..."
					//Get PAM Id for existing patient/CPR
					def ident = getIdentOfExistingCPR(cpr, identURL)
					//Create the encounter
					createEncounterUsingSOAPMessage(ident, "IMP", url)
				}
			}
			else {
				println "Error occurred"
				println "--------------"
				println e.printStackTrace();
			}
		}
	}

	private static void createEncounterUsingSOAPMessage(String pamId, String encounterType, String url){
		def typeCodeId = ""
		if (encounterType.equalsIgnoreCase("IMP")) {
			typeCodeId = "CON-X2710250"; // type code for inpatient encounter
		} else if (encounterType.equalsIgnoreCase("EMER")) {
			typeCodeId = "CON-X2710205"; // type code for emergency encounter
		} else if (encounterType.equalsIgnoreCase("AMB")) {
			typeCodeId = "CON-X2710118"; // type code for ambulatory encounter
		} else
			typeCodeId = "CON-X2710250"; // set to inpatient encounter

		def soapRequest = """
		<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:sch="http://scandihealth.com/ccs/util/schemas" xmlns:sch1="http://scandihealth.com/pam/services/webservice/ccstestsupport/schemas">
			<SOAP-ENV:Header>
				<sch:SecurityInfo>
					<UserId>testuser1</UserId>
					<Password>Testuser-01</Password>
				</sch:SecurityInfo>
			</SOAP-ENV:Header>
			<SOAP-ENV:Body>
				<sch1:CreateEncounterEvent>
					<PatientId>${pamId}</PatientId>
					<TypeCodeId>${typeCodeId}</TypeCodeId>
         			<ClinicalResponsibleOrgRoleId>6494611</ClinicalResponsibleOrgRoleId>
         			<StartDateTime>2017-02-20T00:00:00.000+02:00</StartDateTime>
				</sch1:CreateEncounterEvent>
			</SOAP-ENV:Body>
		</SOAP-ENV:Envelope>"""

		def client = new SOAPClient(url)
		try {
			def response = client.send(soapRequest)
			println "Kontakt oprettet for patient med PAM Id: " + pamId + ".\n"
		}
		catch (e) {
			// If something goes wrong, show the error
			println "Error occurred when creating encounter"
			println "--------------"
			println e.printStackTrace();
		}
	}

	private static String getIdentOfExistingCPR(String cpr, String identURL){
		def root = "";
		if (!cpr.matches(".*[a-zA-Z].*")) { // if it is a real CPR
			root = "1.3.6.1.4.1.25208.10.20"
		} else { // if it is a replacement CPR
			root = "1.3.6.1.4.1.25208.20.81.20.10.30.2"
		}
		def soapRequest = """
		<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:sch="http://scandihealth.com/ccs/util/schemas" xmlns:sch1="http://scandihealth.com/pam/services/webservice/ccspatientexternal/schemas">
			<SOAP-ENV:Header>
				<sch:SecurityInfo>
					<UserId>testuser1</UserId>
					<Password>Testuser-01</Password>
			</sch:SecurityInfo>
			</SOAP-ENV:Header>
			<SOAP-ENV:Body>
				<sch1:getByIdent>
					<ident>
            			<Root>${root}</Root>
						<Extension>${cpr}</Extension>
         			</ident>
				</sch1:getByIdent>
			</SOAP-ENV:Body>
		</SOAP-ENV:Envelope>"""

		def client = new SOAPClient(identURL)
		try {
			def response = client.send(soapRequest)
			def body = response.body.toString()
			def pamId = body.substring(body.lastIndexOf(".10")+3)
			println "Fandt PAM Id " +pamId+  " for eksisterende patient."
			return pamId
		}
		catch (e) {
			println "Error occurred"
			println "--------------"
			println e.printStackTrace();
		}
	}
}
