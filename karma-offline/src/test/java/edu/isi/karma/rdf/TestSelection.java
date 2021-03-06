package edu.isi.karma.rdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.config.ModelingConfiguration;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.ICommand.CommandTag;
import edu.isi.karma.controller.command.selection.Selection;
import edu.isi.karma.controller.command.selection.SuperSelectionManager;
import edu.isi.karma.controller.history.WorksheetCommandHistoryExecutor;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.er.helper.PythonRepository;
import edu.isi.karma.imp.json.JsonImport;
import edu.isi.karma.kr2rml.ErrorReport;
import edu.isi.karma.kr2rml.KR2RMLWorksheetRDFGenerator;
import edu.isi.karma.kr2rml.URIFormatter;
import edu.isi.karma.kr2rml.mapping.KR2RMLMapping;
import edu.isi.karma.kr2rml.mapping.R2RMLMappingIdentifier;
import edu.isi.karma.kr2rml.mapping.WorksheetR2RMLJenaModelParser;
import edu.isi.karma.kr2rml.writer.KR2RMLRDFWriter;
import edu.isi.karma.kr2rml.writer.N3KR2RMLRDFWriter;
import edu.isi.karma.metadata.KarmaMetadataManager;
import edu.isi.karma.metadata.PythonTransformationMetadata;
import edu.isi.karma.metadata.UserConfigMetadata;
import edu.isi.karma.metadata.UserPreferencesMetadata;
import edu.isi.karma.rep.Row;
import edu.isi.karma.rep.Table;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.WorkspaceManager;
import edu.isi.karma.util.EncodingDetector;
import edu.isi.karma.util.JSONUtil;
import edu.isi.karma.webserver.ExecutionController;
import edu.isi.karma.webserver.KarmaException;
import edu.isi.karma.webserver.WorkspaceRegistry;


public class TestSelection {
	private static Logger logger = LoggerFactory.getLogger(TestSelection.class);
	Workspace workspace;
	Worksheet worksheet;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

        KarmaMetadataManager userMetadataManager = new KarmaMetadataManager();
        UpdateContainer uc = new UpdateContainer();
        userMetadataManager.register(new UserPreferencesMetadata(), uc);
        userMetadataManager.register(new UserConfigMetadata(), uc);
        userMetadataManager.register(new PythonTransformationMetadata(), uc);
        PythonRepository.disableReloadingLibrary();
	}
	
	@Before
	public void setUp() throws Exception {
		workspace = WorkspaceManager.getInstance().createWorkspace();
        WorkspaceRegistry.getInstance().register(new ExecutionController(workspace));
        ModelingConfiguration.load();
        ModelingConfiguration.setManualAlignment(true);
        File file = new File(getClass().getClassLoader().getResource("people.json").toURI());
        InputStream is = new FileInputStream(file);
        Reader reader = EncodingDetector.getInputStreamReader(is, EncodingDetector.detect(file));
		Object json = JSONUtil.createJson(reader);
		JsonImport imp = new JsonImport(json, "people.json", workspace, EncodingDetector.detect(file), 1000);
		worksheet = imp.generateWorksheet();
	}
	@Test
	public void testSelection1() throws IOException {
		
		StringBuilder pythonCode = new StringBuilder();
		pythonCode.append("if getValue(\"title\") == \"Prof\": \n");
		pythonCode.append("	 return True \n");
		Selection sel = worksheet.getSelectionManager().createMiniSelection(workspace, worksheet.getId(), 
				worksheet.getHeaders().getId(), 
				pythonCode.toString(), true);
		assertEquals(sel != null, true);
		Table t = worksheet.getDataTable();
		for (Row r : t.getRows(0, t.getNumRows(), SuperSelectionManager.DEFAULT_SELECTION)) {
			boolean t1 = sel.isSelected(r);
			if (r.getNeighborByColumnName("title", workspace.getFactory()).getValue().asString().equals("Prof"))
				assertTrue(t1);
			else
				assertFalse(t1);
		}
	}
	@Test
	public void testSelection2() throws IOException, KarmaException {
		StringBuilder pythonCode = new StringBuilder();
		pythonCode.append("if getValue(\"title\") == \"Prof\": \n");
		pythonCode.append("	 return True \n");
		Selection sel = worksheet.getSelectionManager().createMiniSelection(workspace, worksheet.getId(), 
				worksheet.getHeaders().getId(), 
				pythonCode.toString(), true);
		worksheet.getSelectionManager().updateCurrentSelection(sel.getHTableId(), sel);
		R2RMLMappingIdentifier modelIdentifier = new R2RMLMappingIdentifier(
				"people-model", getTestResource(
						 "people-model.ttl"));
		worksheet.getSuperSelectionManager().defineSelection("test").addSelection(sel.getHTableId());
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		List<KR2RMLRDFWriter> writers = new ArrayList<KR2RMLRDFWriter>();
		writers.add(new N3KR2RMLRDFWriter(new URIFormatter(), pw));
		WorksheetR2RMLJenaModelParser modelParser = new WorksheetR2RMLJenaModelParser(modelIdentifier);
		applyHistoryToWorksheet(workspace, worksheet, modelParser.parse());
		KR2RMLWorksheetRDFGenerator rdfGen = new KR2RMLWorksheetRDFGenerator(worksheet,
		        workspace.getFactory(), workspace.getOntologyManager(), writers,
		        false, modelParser.parse(), new ErrorReport(), worksheet.getSuperSelectionManager().getSuperSelection("test"));
		rdfGen.generateRDF(true);
		String rdf = sw.toString();
		assertNotEquals(rdf.length(), 0);
		String[] lines = rdf.split(System.getProperty("line.separator"));
		assertEquals(37, lines.length);
	}
	
	private URL getTestResource(String name)
	{
		return getClass().getClassLoader().getResource(name);
	}
	
	private void applyHistoryToWorksheet(Workspace workspace, Worksheet worksheet,
			KR2RMLMapping mapping) throws JSONException {
		WorksheetCommandHistoryExecutor wchr = new WorksheetCommandHistoryExecutor(worksheet.getId(), workspace);
		try
		{
			List<CommandTag> tags = new ArrayList<CommandTag>();
			tags.add(CommandTag.Transformation);
			wchr.executeCommandsByTags(tags, mapping.getWorksheetHistory());
		}
		catch (CommandException | KarmaException e)
		{
			logger.error("Unable to execute column transformations", e);
		}
	}
	
}
