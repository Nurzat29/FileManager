import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.MissingResourceException;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TreeAdapter;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

/**
 * File Viewer example
 */
public class Main {

  /* UI elements */
  private Display display;

  private Shell shell;
  
  private ToolBar toolBar;
  
  private Menu popupMenu;
  
  private MenuItem menuItem;
  
  private File currentDirectory = null;

  private boolean initial = true;

  private File[] deferredRefreshFiles = null; // to defer notifyRefreshFiles
                        // while we do DND

  private boolean deferredRefreshRequested = false; // to defer
                            // notifyRefreshFiles
                            // while we do DND

  /* Combo view */
  private static final String COMBODATA_ROOTS = "Combo.roots";

  // File[]: Array of files whose paths are currently displayed in the combo
  private static final String COMBODATA_LASTTEXT = "Combo.lastText";

  // String: Previous selection text string

  private Combo combo;

  private static final String TREEITEMDATA_FILE = "TreeItem.file";

  // File: File associated with tree item
  private static final String TREEITEMDATA_IMAGEEXPANDED = "TreeItem.imageExpanded";

  // Image: shown when item is expanded
  private static final String TREEITEMDATA_IMAGECOLLAPSED = "TreeItem.imageCollapsed";

  // Image: shown when item is collapsed
  private static final String TREEITEMDATA_STUB = "TreeItem.stub";

  // Object: if not present or null then the item has not been populated

  private Tree tree;

  private Label treeScopeLabel;

  /* Table view */
  private static final DateFormat dateFormat = DateFormat
      .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

  private static final String TABLEITEMDATA_FILE = "TableItem.file";

  // File: File associated with table row
  private static final String TABLEDATA_DIR = "Table.dir";

  // File: Currently visible directory
  private static final int[] tableWidths = new int[] { 150, 60, 75, 150 };

  private final String[] tableTitles = new String[] {
		  Main.getResourceString("Имя"),
		  Main.getResourceString("Размер"),
		  Main.getResourceString("Тип"),
		  Main.getResourceString("Дата изменения") };

  private Table table;

  private Label tableContentsOfLabel;

  /* Table update worker */
  // Control data
  private final Object workerLock = new Object();

  // Lock for all worker control data and state
  private volatile Thread workerThread = null;

  // The worker's thread
  private volatile boolean workerStopped = false;

  // True if the worker must exit on completion of the current cycle
  private volatile boolean workerCancelled = false;

  // True if the worker must cancel its operations prematurely perhaps due to
  // a state update

  // Worker state information -- this is what gets synchronized by an update
  private volatile File workerStateDir = null;

  // State information to use for the next cycle
  private volatile File workerNextDir = null;

  /* Simulate only flag */
  // when true, disables actual filesystem manipulations and outputs results
  // to standard out
  private boolean simulateOnly = true;

  /**
   * Runs main program.
   */
  public static void main(String[] args) {
    Display display = new Display();
    Main application = new Main();
    Shell shell = application.open(display);
    while (!shell.isDisposed()) {
      if (!display.readAndDispatch())
        display.sleep();
    }
    application.close();
    display.dispose();
  }

  /**
   * Opens the main program.
   */
  public Shell open(Display display) {
    // Create the window
    this.display = display;
    shell = new Shell();
    createShellContents();
    notifyRefreshFiles(null);
    shell.open();
    return shell;
  }

  /**
   * Closes the main program.
   */
  void close() {
    workerStop();
  }

  /**
   * Returns a string from the resource bundle. We don't want to crash because
   * of a missing String. Returns the key if not found.
   */
  static String getResourceString(String key) {
      return key;
  }

  /**
   * Returns a string from the resource bundle and binds it with the given
   * arguments. If the key is not found, return the key.
   */
  static String getResourceString(String key, Object[] args) {
    try {
      return MessageFormat.format(getResourceString(key), args);
    } catch (MissingResourceException e) {
      return key;
    } catch (NullPointerException e) {
      return "!" + key + "!";
    }
  }

  /**
   * Construct the UI
   * 
   * @param container
   *            the ShellContainer managing the Shell we are rendering inside
   */
  private void createShellContents() {
    shell.setText(getResourceString("Файловый менеджер", new Object[] { "" }));

    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 3;
    gridLayout.marginHeight = gridLayout.marginWidth = 0;
    shell.setLayout(gridLayout);

    GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gridData.widthHint = 185;
    createComboView(shell, gridData);
    gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    gridData.horizontalSpan = 2;
    createToolBar(shell, gridData);

    SashForm sashForm = new SashForm(shell, SWT.NONE);
    sashForm.setOrientation(SWT.HORIZONTAL);
    gridData = new GridData(GridData.FILL_HORIZONTAL
        | GridData.FILL_VERTICAL);
    gridData.horizontalSpan = 3;
    sashForm.setLayoutData(gridData);
    createTreeView(sashForm);
    createTableView(sashForm);
    sashForm.setWeights(new int[] { 2, 5 });
  }


  /**
   * Creates the combo box view.
   * 
   * @param parent
   *            the parent control
   */
  private void createComboView(Composite parent, Object layoutData) {
    combo = new Combo(parent, SWT.NONE);
    combo.setLayoutData(layoutData);
    combo.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent e) {
        final File[] roots = (File[]) combo.getData(COMBODATA_ROOTS);
        if (roots == null)
          return;
        int selection = combo.getSelectionIndex();
        if (selection >= 0 && selection < roots.length) {
          notifySelectedDirectory(roots[selection]);
        }
      }

      public void widgetDefaultSelected(SelectionEvent e) {
        final String lastText = (String) combo
            .getData(COMBODATA_LASTTEXT);
        String text = combo.getText();
        if (text == null)
          return;
        if (lastText != null && lastText.equals(text))
          return;
        combo.setData(COMBODATA_LASTTEXT, text);
        notifySelectedDirectory(new File(text));
      }
    });
  }

  /**
   * Creates the file tree view.
   * 
   * @param parent
   *            the parent control
   */
  private void createTreeView(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 1;
    gridLayout.marginHeight = gridLayout.marginWidth = 2;
    gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
    composite.setLayout(gridLayout);

    treeScopeLabel = new Label(composite, SWT.BORDER);
    treeScopeLabel.setText(Main
        .getResourceString("Директории"));
    treeScopeLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
        | GridData.VERTICAL_ALIGN_FILL));

    tree = new Tree(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL
        | SWT.SINGLE);
    tree.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
        | GridData.FILL_VERTICAL));

    tree.addSelectionListener(new SelectionListener() {
      public void widgetSelected(SelectionEvent event) {
        final TreeItem[] selection = tree.getSelection();
        if (selection != null && selection.length != 0) {
          TreeItem item = selection[0];
          File file = (File) item.getData(TREEITEMDATA_FILE);

          notifySelectedDirectory(file);
        }
      }

      public void widgetDefaultSelected(SelectionEvent event) {
        final TreeItem[] selection = tree.getSelection();
        if (selection != null && selection.length != 0) {
          TreeItem item = selection[0];
          item.setExpanded(true);
          treeExpandItem(item);
        }
      }
    });
    tree.addTreeListener(new TreeAdapter() {
      public void treeExpanded(TreeEvent event) {
        final TreeItem item = (TreeItem) event.item;
        final Image image = (Image) item
            .getData(TREEITEMDATA_IMAGEEXPANDED);
        if (image != null)
          item.setImage(image);
        treeExpandItem(item);
      }

      public void treeCollapsed(TreeEvent event) {
        final TreeItem item = (TreeItem) event.item;
        final Image image = (Image) item
            .getData(TREEITEMDATA_IMAGECOLLAPSED);
        if (image != null)
          item.setImage(image);
      }
    });
  }

  /**
   * Handles expand events on a tree item.
   * 
   * @param item
   *            the TreeItem to fill in
   */
  private void treeExpandItem(TreeItem item) {
    final Object stub = item.getData(TREEITEMDATA_STUB);
    if (stub == null)
      treeRefreshItem(item, true);
  }

  /**
   * Traverse the entire tree and update only what has changed.
   * 
   * @param roots
   *            the root directory listing
   */
  private void treeRefresh(File[] masterFiles) {
    TreeItem[] items = tree.getItems();
    int masterIndex = 0;
    int itemIndex = 0;
    for (int i = 0; i < items.length; ++i) {
      final TreeItem item = items[i];
      final File itemFile = (File) item.getData(TREEITEMDATA_FILE);
      if ((itemFile == null) || (masterIndex == masterFiles.length)) {
        // remove bad item or placeholder
        item.dispose();
        continue;
      }
      final File masterFile = masterFiles[masterIndex];
      int compare = compareFiles(masterFile, itemFile);
      if (compare == 0) {
        // same file, update it
        treeRefreshItem(item, false);
        ++itemIndex;
        ++masterIndex;
      } else if (compare < 0) {
        // should appear before file, insert it
        TreeItem newItem = new TreeItem(tree, SWT.NULL, itemIndex);
        treeInitVolume(newItem, masterFile);
        new TreeItem(newItem, SWT.NULL); // placeholder child item to
                          // get "expand" button
        ++itemIndex;
        ++masterIndex;
        --i;
      } else {
        // should appear after file, delete stale item
        item.dispose();
      }
    }
    for (; masterIndex < masterFiles.length; ++masterIndex) {
      final File masterFile = masterFiles[masterIndex];
      TreeItem newItem = new TreeItem(tree, SWT.NULL);
      treeInitVolume(newItem, masterFile);
      new TreeItem(newItem, SWT.NULL); // placeholder child item to get
                        // "expand" button
    }
  }

  /**
   * Traverse an item in the tree and update only what has changed.
   * 
   * @param dirItem
   *            the tree item of the directory
   * @param forcePopulate
   *            true iff we should populate non-expanded items as well
   */
  private void treeRefreshItem(TreeItem dirItem, boolean forcePopulate) {
    final File dir = (File) dirItem.getData(TREEITEMDATA_FILE);

    if (!forcePopulate && !dirItem.getExpanded()) {
      // Refresh non-expanded item
      if (dirItem.getData(TREEITEMDATA_STUB) != null) {
        treeItemRemoveAll(dirItem);
        new TreeItem(dirItem, SWT.NULL); // placeholder child item to
                          // get "expand" button
        dirItem.setData(TREEITEMDATA_STUB, null);
      }
      return;
    }
    // Refresh expanded item
    dirItem.setData(TREEITEMDATA_STUB, this); // clear stub flag

    /* Get directory listing */
    File[] subFiles = (dir != null) ? Main.getDirectoryList(dir)
        : null;
    if (subFiles == null || subFiles.length == 0) {
      /* Error or no contents */
      treeItemRemoveAll(dirItem);
      dirItem.setExpanded(false);
      return;
    }

    /* Refresh sub-items */
    TreeItem[] items = dirItem.getItems();
    final File[] masterFiles = subFiles;
    int masterIndex = 0;
    int itemIndex = 0;
    File masterFile = null;
    for (int i = 0; i < items.length; ++i) {
      while ((masterFile == null) && (masterIndex < masterFiles.length)) {
        masterFile = masterFiles[masterIndex++];
        if (!masterFile.isDirectory())
          masterFile = null;
      }

      final TreeItem item = items[i];
      final File itemFile = (File) item.getData(TREEITEMDATA_FILE);
      if ((itemFile == null) || (masterFile == null)) {
        // remove bad item or placeholder
        item.dispose();
        continue;
      }
      int compare = compareFiles(masterFile, itemFile);
      if (compare == 0) {
        // same file, update it
        treeRefreshItem(item, false);
        masterFile = null;
        ++itemIndex;
      } else if (compare < 0) {
        // should appear before file, insert it
        TreeItem newItem = new TreeItem(dirItem, SWT.NULL, itemIndex);
        treeInitFolder(newItem, masterFile);
        new TreeItem(newItem, SWT.NULL); // add a placeholder child
                          // item so we get the
                          // "expand" button
        masterFile = null;
        ++itemIndex;
        --i;
      } else {
        // should appear after file, delete stale item
        item.dispose();
      }
    }
    while ((masterFile != null) || (masterIndex < masterFiles.length)) {
      if (masterFile != null) {
        TreeItem newItem = new TreeItem(dirItem, SWT.NULL);
        treeInitFolder(newItem, masterFile);
        new TreeItem(newItem, SWT.NULL); // add a placeholder child
                          // item so we get the
                          // "expand" button
        if (masterIndex == masterFiles.length)
          break;
      }
      masterFile = masterFiles[masterIndex++];
      if (!masterFile.isDirectory())
        masterFile = null;
    }
  }

  /**
   * Foreign method: removes all children of a TreeItem.
   * 
   * @param treeItem
   *            the TreeItem
   */
  private static void treeItemRemoveAll(TreeItem treeItem) {
    final TreeItem[] children = treeItem.getItems();
    for (int i = 0; i < children.length; ++i) {
      children[i].dispose();
    }
  }

  /**
   * Initializes a folder item.
   * 
   * @param item
   *            the TreeItem to initialize
   * @param folder
   *            the File associated with this TreeItem
   */
  private void treeInitFolder(TreeItem item, File folder) {
    item.setText(folder.getName());
    item.setData(TREEITEMDATA_FILE, folder);
  }

  /**
   * Initializes a volume item.
   * 
   * @param item
   *            the TreeItem to initialize
   * @param volume
   *            the File associated with this TreeItem
   */
  private void treeInitVolume(TreeItem item, File volume) {
    item.setText(volume.getPath());
    item.setData(TREEITEMDATA_FILE, volume);
  }
  
  private void createToolBar(final Shell shell, Object layoutData) {
	    toolBar = new ToolBar(shell, SWT.NULL);
	    toolBar.setLayoutData(layoutData);
	    ToolItem item = new ToolItem(toolBar, SWT.SEPARATOR);
	    item = new ToolItem(toolBar, SWT.PUSH);
	    item.setText("Generate MD5");
	    item.setToolTipText(getResourceString("Сгенерировать MD5"));
	    item.addSelectionListener(new SelectionAdapter() {
	      public void widgetSelected(SelectionEvent e) {
	    	  if(table.getSelection().length == 0) {
	    		  MessageBox errorMessage = new MessageBox(shell, SWT.ERROR);
	    		  errorMessage.setText("Ошибка");
	    		  errorMessage.setMessage("Выберите файл!");
	    		  errorMessage.open();
	    	  } else {
	    		  TableItem[] item1 = table.getSelection();
	    		  System.out.println(item1.length);
	    		  MessageBox messageBox = new MessageBox(shell, SWT.ICON_INFORMATION);
	    		  messageBox.setText("Успешно");
	    		  messageBox.setMessage("MD5: " + getMD5(item1[0].toString()));
	    		  messageBox.open();
	    	  }
	      }
	    });
  }
  
  public String getMD5(String st) {
      String md5Hex = DigestUtils.md5Hex(st);
      return md5Hex;
  }
  
  /**
   * Creates the file details table.
   * 
   * @param parent
   *            the parent control
   */
  private void createTableView(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 1;
    gridLayout.marginHeight = gridLayout.marginWidth = 2;
    gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
    composite.setLayout(gridLayout);
    tableContentsOfLabel = new Label(composite, SWT.BORDER);
    tableContentsOfLabel.setLayoutData(new GridData(
        GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_FILL));

    table = new Table(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL
        | SWT.MULTI | SWT.FULL_SELECTION);
    table.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
        | GridData.FILL_VERTICAL));

    for (int i = 0; i < tableTitles.length; ++i) {
      TableColumn column = new TableColumn(table, SWT.NONE);
      column.setText(tableTitles[i]);
      column.setWidth(tableWidths[i]);
    }
    table.setHeaderVisible(true);
    table.addSelectionListener(new SelectionAdapter() {
      public void widgetSelected(SelectionEvent event) {
    	  if(table.getSelection().length != 0) {
    		  System.out.println(table.getSelection().length); 
    		  popupMenu = new Menu(table);
        	  menuItem = new MenuItem(popupMenu, SWT.NONE);
       		  menuItem.setText("generate MD5");
       		  table.setMenu(popupMenu);
    	      menuItem.addListener(SWT.Selection, new Listener() {
				
				
				public void handleEvent(Event arg0) {
					if(table.getSelection().length != 0) {
						TableItem[] item1 = table.getSelection();
						MessageBox messageBox = new MessageBox(shell, SWT.ICON_INFORMATION);
			    		messageBox.setText("Успешно");
			    		messageBox.setMessage("MD5: " + getMD5(item1[0].toString()));
			    		messageBox.open();	
					} else {
						MessageBox messageBox = new MessageBox(shell, SWT.ICON_INFORMATION);
			    		messageBox.setText("Ошибка");
			    		messageBox.setMessage("Выберите файл!");
			    		messageBox.open();
					}
				}
			});
    	  } else {
    		  System.out.println("not selected");
    		  popupMenu.setVisible(false);
    	  }
      }

      public void widgetDefaultSelected(SelectionEvent event) {
        doDefaultFileAction(getSelectedFiles());
      }

      private File[] getSelectedFiles() {
        final TableItem[] items = table.getSelection();
        final File[] files = new File[items.length];

        for (int i = 0; i < items.length; ++i) {
          files[i] = (File) items[i].getData(TABLEITEMDATA_FILE);
        }
        return files;
      }
    });
  }

  /**
   * Notifies the application components that a new current directory has been
   * selected
   * 
   * @param dir
   *            the directory that was selected, null is ignored
   */
  void notifySelectedDirectory(File dir) {
    if (dir == null)
      return;
    if (currentDirectory != null && dir.equals(currentDirectory))
      return;
    currentDirectory = dir;

    /*
     * Shell: Sets the title to indicate the selected directory
     */
    shell.setText(getResourceString("Файловый менеджер",
        new Object[] { currentDirectory.getPath() }));

    /*
     * Table view: Displays the contents of the selected directory.
     */
    workerUpdate(dir, false);

    /*
     * Combo view: Sets the combo box to point to the selected directory.
     */
    final File[] comboRoots = (File[]) combo.getData(COMBODATA_ROOTS);
    int comboEntry = -1;
    if (comboRoots != null) {
      for (int i = 0; i < comboRoots.length; ++i) {
        if (dir.equals(comboRoots[i])) {
          comboEntry = i;
          break;
        }
      }
    }
    if (comboEntry == -1)
      combo.setText(dir.getPath());
    else
      combo.select(comboEntry);

    /*
     * Tree view: If not already expanded, recursively expands the parents
     * of the specified directory until it is visible.
     */
    Vector /* of File */path = new Vector();
    // Build a stack of paths from the root of the tree
    while (dir != null) {
      path.add(dir);
      dir = dir.getParentFile();
    }
    // Recursively expand the tree to get to the specified directory
    TreeItem[] items = tree.getItems();
    TreeItem lastItem = null;
    for (int i = path.size() - 1; i >= 0; --i) {
      final File pathElement = (File) path.elementAt(i);

      // Search for a particular File in the array of tree items
      // No guarantee that the items are sorted in any recognizable
      // fashion, so we'll
      // just sequential scan. There shouldn't be more than a few thousand
      // entries.
      TreeItem item = null;
      for (int k = 0; k < items.length; ++k) {
        item = items[k];
        if (item.isDisposed())
          continue;
        final File itemFile = (File) item.getData(TREEITEMDATA_FILE);
        if (itemFile != null && itemFile.equals(pathElement))
          break;
      }
      if (item == null)
        break;
      lastItem = item;
      if (i != 0 && !item.getExpanded()) {
        treeExpandItem(item);
        item.setExpanded(true);
      }
      items = item.getItems();
    }
    tree.setSelection((lastItem != null) ? new TreeItem[] { lastItem }
        : new TreeItem[0]);
  }

  

  /**
   * Notifies the application components that files must be refreshed
   * 
   * @param files
   *            the files that need refreshing, empty array is a no-op, null
   *            refreshes all
   */
  void notifyRefreshFiles(File[] files) {
    if (files != null && files.length == 0)
      return;

    if ((deferredRefreshRequested) && (deferredRefreshFiles != null)
        && (files != null)) {
      // merge requests
      File[] newRequest = new File[deferredRefreshFiles.length
          + files.length];
      System.arraycopy(deferredRefreshFiles, 0, newRequest, 0,
          deferredRefreshFiles.length);
      System.arraycopy(files, 0, newRequest, deferredRefreshFiles.length,
          files.length);
      deferredRefreshFiles = newRequest;
    } else {
      deferredRefreshFiles = files;
      deferredRefreshRequested = true;
    }
    handleDeferredRefresh();
  }

  /**
   * Handles deferred Refresh notifications (due to Drag & Drop)
   */
  void handleDeferredRefresh() {
    if (!deferredRefreshRequested)
      return;
    deferredRefreshRequested = false;
    File[] files = deferredRefreshFiles;
    deferredRefreshFiles = null;

    /*
     * Table view: Refreshes information about any files in the list and
     * their children.
     */
    boolean refreshTable = false;
    if (files != null) {
      for (int i = 0; i < files.length; ++i) {
        final File file = files[i];
        if (file.equals(currentDirectory)) {
          refreshTable = true;
          break;
        }
        File parentFile = file.getParentFile();
        if ((parentFile != null)
            && (parentFile.equals(currentDirectory))) {
          refreshTable = true;
          break;
        }
      }
    } else
      refreshTable = true;
    if (refreshTable)
      workerUpdate(currentDirectory, true);

    /*
     * Combo view: Refreshes the list of roots
     */
    final File[] roots = getRoots();

    if (files == null) {
      boolean refreshCombo = false;
      final File[] comboRoots = (File[]) combo.getData(COMBODATA_ROOTS);

      if ((comboRoots != null) && (comboRoots.length == roots.length)) {
        for (int i = 0; i < roots.length; ++i) {
          if (!roots[i].equals(comboRoots[i])) {
            refreshCombo = true;
            break;
          }
        }
      } else
        refreshCombo = true;

      if (refreshCombo) {
        combo.removeAll();
        combo.setData(COMBODATA_ROOTS, roots);
        for (int i = 0; i < roots.length; ++i) {
          final File file = roots[i];
          combo.add(file.getPath());
        }
      }
    }

    /*
     * Tree view: Refreshes information about any files in the list and
     * their children.
     */
    treeRefresh(roots);

    // Remind everyone where we are in the filesystem
    final File dir = currentDirectory;
    currentDirectory = null;
    notifySelectedDirectory(dir);
  }

  /**
   * Performs the default action on a set of files.
   * 
   * @param files
   *            the array of files to process
   */
  void doDefaultFileAction(File[] files) {
    // only uses the 1st file (for now)
    if (files.length == 0)
      return;
    final File file = files[0];

    if (file.isDirectory()) {
      notifySelectedDirectory(file);
    } else {
      final String fileName = file.getAbsolutePath();
      if (!Program.launch(fileName)) {
        MessageBox dialog = new MessageBox(shell, SWT.ICON_ERROR
            | SWT.OK);
        dialog
            .setMessage(getResourceString(
                "error.FailedLaunch.message",
                new Object[] { fileName }));
        dialog.setText(shell.getText());
        dialog.open();
      }
    }
  }

  /**
   * Navigates to the parent directory
   */
  void doParent() {
    if (currentDirectory == null)
      return;
    File parentDirectory = currentDirectory.getParentFile();
    notifySelectedDirectory(parentDirectory);
  }

  /**
   * Performs a refresh
   */
  void doRefresh() {
    notifyRefreshFiles(null);
  }

  /**
   * Gets filesystem root entries
   * 
   * @return an array of Files corresponding to the root directories on the
   *         platform, may be empty but not null
   */
  File[] getRoots() {
    /*
     * On JDK 1.22 only...
     */
    // return File.listRoots();
    /*
     * On JDK 1.1.7 and beyond... -- PORTABILITY ISSUES HERE --
     */
    if (System.getProperty("os.name").indexOf("Windows") != -1) {
      Vector /* of File */list = new Vector();
      for (char i = 'c'; i <= 'z'; ++i) {
        File drive = new File(i + ":" + File.separator);
        if (drive.isDirectory() && drive.exists()) {
          list.add(drive);
          if (initial && i == 'c') {
            currentDirectory = drive;
            initial = false;
          }
        }
      }
      File[] roots = (File[]) list.toArray(new File[list.size()]);
      sortFiles(roots);
      return roots;
    } else {
      File root = new File(File.separator);
      if (initial) {
        currentDirectory = root;
        initial = false;
      }
      return new File[] { root };
    }
  }

  /**
   * Gets a directory listing
   * 
   * @param file
   *            the directory to be listed
   * @return an array of files this directory contains, may be empty but not
   *         null
   */
  static File[] getDirectoryList(File file) {
    File[] list = file.listFiles();
    if (list == null)
      return new File[0];
    sortFiles(list);
    return list;
  }

  /**
   * Sorts files lexicographically by name.
   * 
   * @param files
   *            the array of Files to be sorted
   */
  static void sortFiles(File[] files) {
    /* Very lazy merge sort algorithm */
    sortBlock(files, 0, files.length - 1, new File[files.length]);
  }

  private static void sortBlock(File[] files, int start, int end,
      File[] mergeTemp) {
    final int length = end - start + 1;
    if (length < 8) {
      for (int i = end; i > start; --i) {
        for (int j = end; j > start; --j) {
          if (compareFiles(files[j - 1], files[j]) > 0) {
            final File temp = files[j];
            files[j] = files[j - 1];
            files[j - 1] = temp;
          }
        }
      }
      return;
    }
    final int mid = (start + end) / 2;
    sortBlock(files, start, mid, mergeTemp);
    sortBlock(files, mid + 1, end, mergeTemp);
    int x = start;
    int y = mid + 1;
    for (int i = 0; i < length; ++i) {
      if ((x > mid)
          || ((y <= end) && compareFiles(files[x], files[y]) > 0)) {
        mergeTemp[i] = files[y++];
      } else {
        mergeTemp[i] = files[x++];
      }
    }
    for (int i = 0; i < length; ++i)
      files[i + start] = mergeTemp[i];
  }

  private static int compareFiles(File a, File b) {
    // boolean aIsDir = a.isDirectory();
    // boolean bIsDir = b.isDirectory();
    // if (aIsDir && ! bIsDir) return -1;
    // if (bIsDir && ! aIsDir) return 1;

    // sort case-sensitive files in a case-insensitive manner
    int compare = a.getName().compareToIgnoreCase(b.getName());
    if (compare == 0)
      compare = a.getName().compareTo(b.getName());
    return compare;
  }

  /*
   * This worker updates the table with file information in the background.
   * <p> Implementation notes: <ul> <li> It is designed such that it can be
   * interrupted cleanly. <li> It uses asyncExec() in some places to ensure
   * that SWT Widgets are manipulated in the right thread. Exclusive use of
   * syncExec() would be inappropriate as it would require a pair of context
   * switches between each table update operation. </ul> </p>
   */

  /**
   * Stops the worker and waits for it to terminate.
   */
  void workerStop() {
    if (workerThread == null)
      return;
    synchronized (workerLock) {
      workerCancelled = true;
      workerStopped = true;
      workerLock.notifyAll();
    }
    while (workerThread != null) {
      if (!display.readAndDispatch())
        display.sleep();
    }
  }

  /**
   * Notifies the worker that it should update itself with new data. Cancels
   * any previous operation and begins a new one.
   * 
   * @param dir
   *            the new base directory for the table, null is ignored
   * @param force
   *            if true causes a refresh even if the data is the same
   */
  void workerUpdate(File dir, boolean force) {
    if (dir == null)
      return;
    if ((!force) && (workerNextDir != null) && (workerNextDir.equals(dir)))
      return;

    synchronized (workerLock) {
      workerNextDir = dir;
      workerStopped = false;
      workerCancelled = true;
      workerLock.notifyAll();
    }
    if (workerThread == null) {
      workerThread = new Thread(workerRunnable);
      workerThread.start();
    }
  }

  /**
   * Manages the worker's thread
   */
  private final Runnable workerRunnable = new Runnable() {
    public void run() {
      while (!workerStopped) {
        synchronized (workerLock) {
          workerCancelled = false;
          workerStateDir = workerNextDir;
        }
        workerExecute();
        synchronized (workerLock) {
          try {
            if ((!workerCancelled)
                && (workerStateDir == workerNextDir))
              workerLock.wait();
          } catch (InterruptedException e) {
          }
        }
      }
      workerThread = null;
      // wake up UI thread in case it is in a modal loop awaiting thread
      // termination
      // (see workerStop())
      display.wake();
    }
  };

  /**
   * Updates the table's contents
   */
  private void workerExecute() {
    File[] dirList;
    // Clear existing information
    display.syncExec(new Runnable() {
      public void run() {
        tableContentsOfLabel.setText(Main.getResourceString(
            "Содержание директории",
            new Object[] { workerStateDir.getPath() }));
        table.removeAll();
        table.setData(TABLEDATA_DIR, workerStateDir);
      }
    });
    dirList = getDirectoryList(workerStateDir);

    for (int i = 0; (!workerCancelled) && (i < dirList.length); i++) {
      workerAddFileDetails(dirList[i]);
    }

  }

  /**
   * Adds a file's detail information to the directory list
   */
  private void workerAddFileDetails(final File file) {
    final String nameString = file.getName();
    final String dateString = dateFormat.format(new Date(file
        .lastModified()));
    final String sizeString;
    final String typeString;
    final Image iconImage;

    if (file.isDirectory()) {
      typeString = getResourceString("Папка");
      sizeString = "";
      iconImage = getImage(file);
    } else {
    	sizeString = (file.length()/1024) + "KB";

      int dot = nameString.lastIndexOf('.');
      if (dot != -1) {
        String extension = nameString.substring(dot);
        Program program = Program.findProgram(extension);
        if (program != null) {
          typeString = program.getName();
        } else {
          typeString = getResourceString("Файл",
              new Object[] { extension.toUpperCase() });
        }
      } else {
        typeString = getResourceString("Файл");
      }
    }
    final String[] strings = new String[] { nameString, sizeString,
        typeString, dateString };

    display.syncExec(new Runnable() {
      public void run() {
        // guard against the shell being closed before this runs
        if (shell.isDisposed())
          return;
        TableItem tableItem = new TableItem(table, 0);
        tableItem.setText(strings);
        tableItem.setImage(getImage(file));
        tableItem.setData(TABLEITEMDATA_FILE, file);
      }
    });
  }
  
  public Image getImage(File file) {
      ImageIcon systemIcon = (ImageIcon) FileSystemView.getFileSystemView().getSystemIcon(file);
      java.awt.Image image = systemIcon.getImage();

      int width = image.getWidth(null);
      int height = image.getHeight(null);
      BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2d = bufferedImage.createGraphics();
      g2d.drawImage(image, 0, 0, null);
      g2d.dispose();
      int[] data = ((DataBufferInt) bufferedImage.getData().getDataBuffer()).getData();
      ImageData imageData = new ImageData(width, height, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
      imageData.setPixels(0, 0, data.length, data, 0);
      Image swtImage = new Image(this.display, imageData);
      return swtImage;
  }
}
