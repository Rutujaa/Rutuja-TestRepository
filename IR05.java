import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.jsoup.Jsoup;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import java.io.*;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class IR05 {

	public static String docsPath = "cacm";
	public static Analyzer analyzer = new SimpleAnalyzer();
	public static Directory directory = new RAMDirectory();
	public static IndexReader reader;
	public static IndexSearcher isearcher;
	public static double avgprec, avgfmeasure, avgrecall = 0;
	public static int pcnt, fmcnt, rcnt = 0;

	public static ArrayList<ScoreDoc[]> hitsList = new ArrayList<ScoreDoc[]>();

	public static void main(String[] args) throws Exception {
		// args[0] is absolute path to file
		final Path docDir = Paths.get(docsPath);

		Date start = new Date();
		try {
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			iwc.setOpenMode(OpenMode.CREATE);
			iwc.setRAMBufferSizeMB(128.0);
			IndexWriter writer = new IndexWriter(directory, iwc);

			indexDocs(writer, docDir);

			writer.close();
			Date end = new Date();
			System.out.println(end.getTime() - start.getTime() + " total milliseconds");

			reader = DirectoryReader.open(directory);
			isearcher = new IndexSearcher(reader);

			buildQueryText("cacm.query.xml");

			getRelevantDocuments();
		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}

	}

	/*
	 * It Indexes the given file using the given writer recurses over files and
	 * directories found under the given directory.
	 */

	static void indexDocs(final IndexWriter writer, Path path) throws IOException {
		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					try {
						indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
					} catch (IOException ignore) {
						// don't index files that can't be read.
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
		}
	}

	/* Indexes a single document */
	static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {

			String name = file.getFileName().toString();
			name = name.replaceAll(".html", "");
			Document doc = new Document();

			byte[] fileBytes = Files.readAllBytes(file);
			String fileText = new String(fileBytes);
			String htmlContent = Jsoup.parse(fileText).text().trim();

			doc.add(new StringField("name", name, Field.Store.YES));
			doc.add(new TextField("htmlContent", new StringReader(htmlContent)));
			writer.addDocument(doc);
		}
	}

	/*
	 * parses the XML file into index in RAM memory Executes a Boolean OR query
	 * over the queryText terms Also returns the topHits
	 */
	public static void buildQueryText(String xmlFilePath) throws XMLStreamException, IOException, ParseException {

		// initiating XML stream reader
		FileInputStream fileInputStream = new FileInputStream(xmlFilePath);

		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLStreamReader staxReader = factory.createXMLStreamReader(fileInputStream);

		int event;
		int number = 0;
		while (staxReader.hasNext()) {
			event = staxReader.next();
			switch (event) {
			// reading an opening XML tag
			case XMLStreamConstants.START_ELEMENT:
				String localName = staxReader.getLocalName();
				if (localName.equals("number")) {
					number = Integer.parseInt(staxReader.getElementText());
					System.out.println(number);
				}
				if (localName.equals("text")) {
					String text = staxReader.getElementText().trim();
					String[] terms = text.split(" +");
					String ORText = String.join(" OR ", terms);
					System.out.println(ORText);
					ScoreDoc[] hits = search(ORText, "htmlContent", Integer.MAX_VALUE);
					hitsList.add(number - 1, hits);
					System.out.println("\nhits: " + hits.length);
				}
				break;
			case XMLStreamConstants.CHARACTERS:
				break;
			case XMLStreamConstants.END_ELEMENT:
				break;
			}
		}
	}

	/*
	 * Parses the Query and returns number of hits
	 */
	public static ScoreDoc[] search(String queryText, String fieldName, int topHits)
			throws IOException, ParseException {
		QueryParser parser = new QueryParser(fieldName, analyzer);
		Query query = parser.parse(queryText);
		ScoreDoc[] hits = isearcher.search(query, topHits).scoreDocs;
		return hits;
	}
	/*
	 * The relevant documents are calculated and stored in an ArrayList.
	 * 
	 */

	public static void getRelevantDocuments() {
		// System.out.println(hitsList.get(62));
		try {
			BufferedReader file = new BufferedReader(new FileReader("cacm.rel.txt"));
			String readLine = "";
			Integer qNum = 1;
			ArrayList<String> docs = new ArrayList<String>();
			while ((readLine = file.readLine()) != null) {
				String[] row = readLine.split(" ");
				if (qNum != Integer.parseInt(row[0])) {
					// function call for calculating precision,FMeasure and
					// Recall
					getCalculation(qNum, hitsList.get(qNum - 1), docs);
					docs.clear();
				}
				qNum = Integer.parseInt(row[0]);
				docs.add(row[2]);
			}
			System.out.println("Average Precision : " + avgprec / pcnt + "\tAverage Recall = :" + avgrecall / rcnt
					+ "\tAverage FMeasure :" + avgfmeasure / fmcnt + "\n");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * This fuction calculates Precision ,Recall and FMeasure It does not return
	 * any value. To calculate average Global variable are declared and used.
	 */

	public static void getCalculation(Integer qNum, ScoreDoc[] hitsArray, ArrayList<String> docs) {
		int matchingDocs = 0;
		for (ScoreDoc scoreDoc : hitsArray) {
			try {
				String scoreDocName = isearcher.doc(scoreDoc.doc).get("name");

				if (docs.contains(scoreDocName)) {
					matchingDocs++;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		double precision = (double) matchingDocs / (double) hitsArray.length;
		avgprec = avgprec + precision;
		pcnt++;
		System.out.println("Query #" + qNum + ": Precicion: " + precision);

		double recall = (double) matchingDocs / (double) (docs.size());
		avgrecall = avgrecall + recall;
		rcnt++;
		System.out.println("Query #" + qNum + ": Recall: " + recall);

		double betasquare = 1;
		double fmeasure = ((betasquare + 1) * precision * recall) / (precision + recall);
		avgfmeasure = avgfmeasure + fmeasure;
		fmcnt++;
		System.out.println("Query #" + qNum + ": FMeasure: " + fmeasure + "\n");

	}
}
