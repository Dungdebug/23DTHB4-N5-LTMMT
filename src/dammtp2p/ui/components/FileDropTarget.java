package dammtp2p.ui.components;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Handles Drag & Drop of local files onto a Swing component.
 */
public class FileDropTarget extends DropTargetAdapter {

    private final JComponent targetComponent;
    private final Consumer<File> fileDropListener;
    private final Runnable dragEnterListener;
    private final Runnable dragExitListener;

    public FileDropTarget(JComponent component, Consumer<File> fileDropListener) {
        this(component, fileDropListener, null, null);
    }

    public FileDropTarget(JComponent component,
                          Consumer<File> fileDropListener,
                          Runnable dragEnterListener,
                          Runnable dragExitListener) {
        this.targetComponent = component;
        this.fileDropListener = fileDropListener;
        this.dragEnterListener = dragEnterListener;
        this.dragExitListener = dragExitListener;

        new DropTarget(component, DnDConstants.ACTION_COPY, this, true, null);
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
            if (dragEnterListener != null) {
                SwingUtilities.invokeLater(dragEnterListener);
            }
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        if (dragExitListener != null) {
            SwingUtilities.invokeLater(dragExitListener);
        }
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        if (dragExitListener != null) {
            SwingUtilities.invokeLater(dragExitListener);
        }

        try {
            Transferable tr = dtde.getTransferable();
            if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                if (files != null && !files.isEmpty()) {
                    File file = files.get(0);
                    if (file.isFile()) {
                        dtde.dropComplete(true);
                        if (fileDropListener != null) {
                            SwingUtilities.invokeLater(() -> fileDropListener.accept(file));
                        }
                        return;
                    }
                }
            }
            dtde.rejectDrop();
        } catch (Exception ex) {
            dtde.rejectDrop();
        }
    }

    private boolean isDragAcceptable(DropTargetDragEvent dtde) {
        return dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }
}