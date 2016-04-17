package org.aksw.qa.commons.load;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.aksw.qa.commons.datastructure.IQuestion;
import org.aksw.qa.commons.datastructure.Question;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * 
 * @author ricardousbeck tortugaattack
 *
 */
public class QALD_Loader {
	static Logger log = LoggerFactory.getLogger(QALD_Loader.class);

	private static InputStream getInputStream(Dataset set) throws IOException {
		// Magical get the path from qa-datasets
		URL url = mapDatasetToPath(set);
		return url.openStream();
	}

	private static String getInputStreamURL(Dataset set) throws IOException {
		// Returns Extension for decision on XML/JSON method
		URL url = mapDatasetToPath(set);

		return url.toString();
	}

	private static URL mapDatasetToPath(Dataset set) {
		// FIXME QALD-5 is that multilingual or hybrid? Load both!
		switch (set) {
	//	case nlq:
	//		return ClassLoader.getSystemClassLoader().getResource("NLQ-OKBQA/nlq1_vis.json");
		case QALD5_Test:
			return ClassLoader.getSystemClassLoader().getResource("QALD-5/qald-5_test.xml");
		case QALD6_Train_Hybrid:
			return ClassLoader.getSystemClassLoader().getResource("QALD-6/qald-6-train-hybrid.json");
		case QALD6_Train_Multilingual:
			return ClassLoader.getSystemClassLoader().getResource("QALD-6/qald-6-train-multilingual.json");
			//FIXME datacube und qbench sollte gleich sein?!Konrad Höffner Fragen
//		case QALD6_Train_Datacube:
//			return ClassLoader.getSystemClassLoader().getResource("QALD-6/qald-6-train-datacube.json");
		case QALD5_Train:
			return ClassLoader.getSystemClassLoader().getResource("QALD-5/qald-5_train.xml");
	//	case qbench1:
	//		return ClassLoader.getSystemClassLoader().getResource("qbench/qbench1.xml");
	//	case qbench2:
	//		return ClassLoader.getSystemClassLoader().getResource("qbench/qbench2.xml");
	//	case stonetemple:
	//		return ClassLoader.getSystemClassLoader().getResource("stonetemple/stonetemple");

		}
		return null;
	}

	public static List<IQuestion> load(Dataset data) {
		try {
			InputStream is = null;
			is = getInputStream(data);
			String isURL = getInputStreamURL(data);
			if (is.available() > 0) // check if stream is not empty
			{
				// decides between xml and json based on URL ending
				if (isURL.toLowerCase().endsWith("xml")) {
					log.info("Loading XML file [" + isURL + "].");
					List<IQuestion> ret = loadXML(is);
					is.close();
					return ret;
				}
				if (isURL.toLowerCase().endsWith("json")) {
					log.info("Loading JSON file [" + isURL + "].");

					List<IQuestion> ret = loadJSON(is);
					is.close();
					return ret;
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	// TODO separate checking for non-empty input stream in all separate load
	// instances necessary? -Christian
	// oldtodo check that input stream is not empty before parsing(Deez Nuts!)
	/**
	 * This methods loads QALD XML files (used in QALD 1 to QALD 5)
	 * 
	 * @param file
	 * @return
	 */
	public static List<IQuestion> loadXML(InputStream file) {
		List<IQuestion> questions = new ArrayList<IQuestion>();

		try {
			if (file.available() > 0) // check if stream is not empty
			{
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc;
				doc = db.parse(file);
				doc.getDocumentElement().normalize();

				NodeList questionNodes = doc.getElementsByTagName("question");

				for (int i = 0; i < questionNodes.getLength(); i++) {

					IQuestion question = new Question();
					Element questionNode = (Element) questionNodes.item(i);

					question.setId(Integer.valueOf(questionNode.getAttribute("id")));
					question.setAnswerType(questionNode.getAttribute("answertype"));
					question.setAggregation(Boolean.valueOf(questionNode.getAttribute("aggregation")));
					question.setOnlydbo(Boolean.valueOf(questionNode.getAttribute("onlydbo")));
					question.setHybrid(Boolean.valueOf(questionNode.getAttribute("hybrid")));

					// Read question
					NodeList nlrs = questionNode.getElementsByTagName("string");
					for (int j = 0; j < nlrs.getLength(); j++) {
						String lang = ((Element) nlrs.item(j)).getAttribute("lang");
						question.getLanguageToQuestion().put(lang, ((Element) nlrs.item(j)).getTextContent().trim());
					}

					// read keywords
					NodeList keywords = questionNode.getElementsByTagName("keywords");
					for (int j = 0; j < keywords.getLength(); j++) {
						String lang = ((Element) keywords.item(j)).getAttribute("lang");
						question.getLanguageToKeywords().put(lang, Arrays.asList(((Element) keywords.item(j)).getTextContent().trim().split(", ")));
					}

					// Read pseudoSPARQL query
					Element element = (Element) questionNode.getElementsByTagName("pseudoquery").item(0);
					if (element != null && element.hasChildNodes()) {
						NodeList childNodes = element.getChildNodes();
						Node item = childNodes.item(0);
						question.setPseudoSparqlQuery(item.getNodeValue().trim());
					}

					// Read SPARQL query
					// checks also that the text node containing query is not
					// null
					element = (Element) questionNode.getElementsByTagName("query").item(0);
					if (element != null && element.hasChildNodes()) {
						NodeList childNodes = element.getChildNodes();
						Node item = childNodes.item(0);
						question.setSparqlQuery(item.getNodeValue().trim());
					}
					// check if OUT OF SCOPE marked
					if (question.getPseudoSparqlQuery() != null) {
						question.setOutOfScope(question.getPseudoSparqlQuery().toUpperCase().contains("OUT OF SCOPE"));
					}
					// check if OUT OF SCOPE marked
					if (question.getSparqlQuery() != null) {
						question.setOutOfScope(question.getSparqlQuery().toUpperCase().contains("OUT OF SCOPE"));
					}
					// Read answers
					NodeList answers = questionNode.getElementsByTagName("answer");
					HashSet<String> set = new HashSet<String>();
					for (int j = 0; j < answers.getLength(); j++) {
						String answer = ((Element) answers.item(j)).getTextContent();
						set.add(answer.trim());
					}
					question.setGoldenAnswers(set);

					questions.add(question);
				}
			}

		} catch (DOMException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return questions;
	}

	/**
	 * This method loads QALD JSON files as used in QALD 6
	 * 
	 * @param file
	 * @return
	 */
	public static List<IQuestion> loadJSON(InputStream file) {
		// TODO Catch exceptions
		List<IQuestion> output = new ArrayList<IQuestion>();
		try {
			if (file.available() > 0) // check if stream is not empty
			{
				JsonReader jsonReader = Json.createReader(file);
				JsonObject mainJsonObject = jsonReader.readObject();
				// JsonObject innerObject =jsonObject.getJsonObject("dataset");
				JsonArray jArray = mainJsonObject.getJsonArray("questions");

				String attributes[] = { "id", "aggregation", "answertype", "onlydbo", "hybrid" };

				for (JsonValue questionJsonObj : jArray) {
					JsonObject listObj = (JsonObject) questionJsonObj;
					IQuestion q = new Question();
					for (String attr : attributes) {
						if (listObj.containsKey(attr)) {
							String val = listObj.get(attr).toString().replace("\"", "");
							q.setValue(attr, val);
						}
					}
					output.add(q);

					JsonArray questionArray = listObj.getJsonArray("question");
					for (JsonValue questionVal : questionArray) {
						JsonObject questionObj = (JsonObject) questionVal;
						String lang = questionObj.getString("language");
						q.getLanguageToQuestion().put(lang, questionObj.getString("string").trim());
						if (questionObj.containsKey("keywords")) {
							List<String> keywords = Arrays.asList(questionObj.getString("keywords").split(","));
							q.getLanguageToKeywords().put(lang, keywords);
						}
					}

					JsonObject query = (JsonObject) listObj.get("query");
					if (query.containsKey("sparql")) {
						String strQuery = query.getString("sparql").trim();
						q.setSparqlQuery(strQuery);
					}
					if (query.containsKey("pseudo")) {
						String strQuery = query.getString("pseudo").trim();
						q.setPseudoSparqlQuery(strQuery);
					}

					JsonArray answerList = listObj.getJsonArray("answers");
					if (!answerList.isEmpty()) {
						JsonObject answerListHead = answerList.getJsonObject(0);
						JsonObject headObject = answerListHead.getJsonObject("head");
						JsonArray vars = headObject.getJsonArray("vars");

						Set<String> answers = new HashSet<String>();
						if (!answerList.isEmpty()) {
							JsonObject answerObject = answerList.getJsonObject(0);
							if (answerObject.containsKey("boolean")) {
								answers.add(answerObject.get("boolean").toString());
							}
							if (answerObject.containsKey("results")) {
								JsonObject resultObject = answerObject.getJsonObject("results");
								JsonArray bindingsList = resultObject.getJsonArray("bindings");
								for (JsonValue bind : bindingsList) {
									JsonObject bindObj = (JsonObject) bind;
									for (JsonValue varName : vars) {
										String var = varName.toString().replaceAll("\"", "");
										if (bindObj.containsKey(var)) {
											JsonObject j = bindObj.getJsonObject(var);
											answers.add(j.getString("value").trim());
										}
									}

								}// end for bindingsList
							}// end if no result set
							q.setGoldenAnswers(answers);
						}// end if Answerlist emptiy

					}// end For questions
				}
			}
		} catch (DOMException e) {
			e.printStackTrace();

		} catch (IOException e) {
			e.printStackTrace();
		}

		/**
		 * Removing Questions with no answers
		 */
		boolean printHappend = false;
		String message = "";
		List<IQuestion> emptyQuestions = new ArrayList<IQuestion>();
		for (IQuestion k : output) {
			if (k.getGoldenAnswers().isEmpty()) {
				emptyQuestions.add(k);
				if (!printHappend) {
					message += "Following Questions (id) have no attached answers: ";
					printHappend = true;
				}
				message += k.getId() + ", ";
			}
		}
		if (printHappend) {
			log.debug(message + " and will be removed");
		}
		output.removeAll(emptyQuestions);
		return output;
	}
}
