package edu.isi.karma.controller.command.worksheet.selection;

import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.command.WorksheetCommand;
import edu.isi.karma.controller.command.selection.LargeSelection.Operation;
import edu.isi.karma.controller.command.selection.Selection;
import edu.isi.karma.controller.command.selection.SelectionManager;
import edu.isi.karma.controller.command.selection.SuperSelection;
import edu.isi.karma.controller.command.selection.SuperSelectionManager;
import edu.isi.karma.controller.update.ErrorUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.controller.update.WorksheetSuperSelectionListUpdate;
import edu.isi.karma.controller.update.WorksheetUpdateFactory;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.HTable;
import edu.isi.karma.rep.RepFactory;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;

public class OperateSelectionCommand extends WorksheetCommand {

	private String hNodeId;
	private String pythonCode;
	private String operation;
	private Selection previousSelection;
	private boolean onError;
	public OperateSelectionCommand(String id, String worksheetId, 
			String hNodeId, String operation, 
			String pythonCode, boolean onError) {
		super(id, worksheetId);
		this.hNodeId = hNodeId;
		this.pythonCode = pythonCode;
		this.operation = operation;
		this.onError = onError;
		addTag(CommandTag.Transformation);
	}

	@Override
	public String getCommandName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getTitle() {
		return "Operate Selection";
	}

	@Override
	public String getDescription() {
		return operation;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.undoable;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {
		//TODO hack for now
		Worksheet worksheet = workspace.getWorksheet(worksheetId);
		RepFactory factory = workspace.getFactory();
		SuperSelection superSel = SuperSelectionManager.DEFAULT_SELECTION;
		HTable hTable = factory.getHTable(factory.getHNode(hNodeId).getHTableId());
		Selection currentSel = worksheet.getSelectionManager().getSelection(hTable.getId());
		Selection anotherSel = null;
		if (!operation.equalsIgnoreCase(Operation.Invert.name())) {
			anotherSel = worksheet.getSelectionManager().createMiniSelection(workspace, worksheetId, hTable.getId(), pythonCode, onError);
		}
		if (currentSel == null && operation.equalsIgnoreCase(Operation.Invert.name()) ) {
			return getErrorUpdate("No defined Selection");
		}
		if (currentSel == null) {
			currentSel = worksheet.getSelectionManager().createMiniSelection(workspace, worksheetId, hTable.getId(), SelectionManager.defaultCode, onError);
		}
		try {
			Operation operation = Operation.valueOf(Operation.class, this.operation);
			Selection t = worksheet.getSelectionManager().createLargeSelection(currentSel, anotherSel, operation);
			if (t == null)
				return getErrorUpdate("Creation unsuccessful");
			previousSelection = worksheet.getSelectionManager().updateCurrentSelection(hTable.getId(), t);
			superSel = worksheet.getSuperSelectionManager().getSuperSelection("DEFAULT_TEST");
			superSel.addSelection(t.getHTableId());
		}catch (Exception e) {
			return getErrorUpdate("The operation is undefined");
		}
		UpdateContainer uc = WorksheetUpdateFactory.createWorksheetHierarchicalAndCleaningResultsUpdates(worksheetId, superSel);
		return uc;
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		Worksheet worksheet = workspace.getWorksheet(worksheetId);
		SuperSelection superSel = worksheet.getSuperSelectionManager().getSuperSelection("DEFAULT_TEST");
		HNode hNode = workspace.getFactory().getHNode(hNodeId);
		worksheet.getSelectionManager().removeSelection(hNode.getHTableId());
		Selection currentSel = worksheet.getSelectionManager().updateCurrentSelection(hNode.getHTableId(), previousSelection);
		if (previousSelection != null)
			superSel.addSelection(previousSelection.getHTableId());
		else if (currentSel != null)
			superSel.removeSelection(currentSel.getHTableId());
		UpdateContainer uc = WorksheetUpdateFactory.createWorksheetHierarchicalAndCleaningResultsUpdates(worksheetId, superSel);	
		uc.add(new WorksheetSuperSelectionListUpdate(worksheetId));
		return uc;
	}
	
	public UpdateContainer getErrorUpdate(String msg) {
		return new UpdateContainer(new ErrorUpdate(msg));
	}
	
	public String getHNodeId() {
		return hNodeId;
	}

}
