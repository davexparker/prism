//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Andrew Hinton <ug60axh@cs.bham.ac.uk> (University of Birmingham)
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford, formerly University of Birmingham)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package userinterface.model.pepaModel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.PlainDocument;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.StyleContext;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import userinterface.model.GUIModelEditor;
import userinterface.model.GUIMultiModelHandler;
import userinterface.model.Style;

public class GUIPepaModelEditor extends GUIModelEditor implements DocumentListener
{	
	private JEditorPane editor;
	private UndoManager undoManager;
	private PlainDocument d;
	private GUIMultiModelHandler handler;
	
	/** Creates a new instance of GUIPepaModelEditor */
	public GUIPepaModelEditor(GUIMultiModelHandler handler)
	{
		editor = new JEditorPane();
		PepaEditorKit kit = new PepaEditorKit();
		editor.setEditorKitForContentType("text/pepa", kit);
		editor.setContentType("text/pepa");
		undoManager = new UndoManager();
		undoManager.setLimit(200);
		//editor.setForeground(FOREGROUND_COLOR);
		this.handler = handler;
		d = (PlainDocument)editor.getDocument();
		editor.getDocument().addDocumentListener(this);
		initComponents();
		
	}
	
	private void initComponents()
	{
		JScrollPane scroller = new JScrollPane();
		{
			scroller.setViewportView(editor);
		}
		setLayout(new BorderLayout());
		add(scroller, BorderLayout.CENTER);
	}
	
	public void changedUpdate(DocumentEvent e)
	{
	}
	
	public void insertUpdate(DocumentEvent e)
	{
		if (handler != null)handler.hasModified(true);
	}
	
	public void removeUpdate(DocumentEvent e)
	{
		if (handler != null)handler.hasModified(true);
	}
	
	public String getParseText()
	{
		return editor.getText();
	}
	
	public void newModel()
	{
		editor.setText("");
	}
	
	public void read(Reader s, Object obj) throws IOException
	{
		editor.read(s, obj);
		d = (PlainDocument)editor.getDocument();
		d.addDocumentListener(this);
		// System.out.println("here");
	}
	
	public void write(Writer s) throws IOException
	{
		editor.write(s);
	}
	
	/** Performs an undo operation on the text in the model editor.
     */
	public void undo() {
		try {
			undoManager.undo();
		} catch (CannotUndoException ex) {
			
			//GUIPrism.getGUI().getMultiLogger().logMessage(PrismLogLevel.PRISM_ERROR, ex.getMessage());
		}
	}
	
	/** Performs a redo operation on the text in the model editor.
     */
	public void redo() {
		try {
			undoManager.redo();
		} catch (CannotRedoException ex) {
			//GUIPrism.getGUI().getMultiLogger().logMessage(PrismLogLevel.PRISM_ERROR, ex.getMessage());
		}
	}
	
	public void copy()
	{
		editor.copy();
	}
	
	public void cut()
	{
		editor.cut();
	}
	
	public void paste()
	{
		editor.paste();
	}
	
	public void delete()
	{
		
	}
	
	public void selectAll()
	{
		editor.selectAll();
	}
	
	public boolean isEditable()
	{
		return editor.isEditable();
	}
	
	public void setEditorFont(Font f)
	{
		editor.setFont(f);
	}
	
	public void setEditorBackground(Color c)
	{
		editor.setBackground(c);
	}
	
	class PepaEditorKit extends DefaultEditorKit
	{
		
		private PepaContext preferences;
		/** Creates a new instance of PrismEditorKit */
		public PepaEditorKit()
		{
			super();
		}
		
		public PepaContext getStylePreferences()
		{
			if (preferences == null)
			{
				preferences = new PepaContext();
			}
			return preferences;
		}
		
		public void setStylePreferences(PepaContext prefs)
		{
			preferences = prefs;
		}
		
		public String getContentType()
		{
			return "text/pepa";
		}
		
		public Document createDefaultDocument()
		{
			return new PlainDocument();
		}
		
		public final ViewFactory getViewFactory()
		{
			return getStylePreferences();
		}
		
		
	}
	
	class PepaContext extends StyleContext implements ViewFactory
	{
		
		
		
		public static final String COMMENT_D = "Single Line Comment";
		
		/** Creates a new instance of PrismContext */
		public PepaContext()
		{
			super();
		}
		
		public View create(Element elem)
		{
			return new PepaView(elem);
		}
		
		
	}
	
	static final Style PLAIN_S = new Style(Color.black, Font.PLAIN);
	class PepaView extends PlainView
	{
		
		private Matcher match;
		private Pattern pattern;
		public PepaView(Element elem)
		{
			super(elem);
			
			pattern = Pattern.compile("%.*");
		}
		
		public void paint(Graphics g, Shape a)
		{
			super.paint(g, a);
		}
		
		@Override
		protected float drawUnselectedText(Graphics2D g, float x, float y, int p0, int p1) throws BadLocationException
		{
			int stLine = findStartOfLine(p0, getDocument());
			int enLine = findEndOfLine(p1, getDocument());
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			try {
				g.setColor(Color.green);
				Document doc = getDocument();
				String s = doc.getText(stLine, enLine-stLine);
				Style[] styles = highlight(s, (p0-stLine), (p1-p0));
				String fname = handler.getPepaEditorFontFast().getName();
				int fsize = handler.getPepaEditorFontFast().getSize();
				for (int curr = 0; curr < styles.length; curr++) {
					Style c = styles[curr];
					g.setColor(c.c);
					g.setFont(new Font(fname, c.style, fsize));
					Segment segm = getLineBuffer();
					doc.getText(p0+curr, 1, segm);
					x = Utilities.drawTabbedText(segm, x, y, g, this, p0+curr);
				}
				g.setColor(Color.black);
				g.setFont(new Font(fname, Font.PLAIN, fsize));
			}
			catch(BadLocationException ex) {
				//System.out.println("ex = "+ex);
				//ex.printStackTrace();
			}
			return x;
		}
		
		@Override
		protected float drawSelectedText(Graphics2D g, float x, float y,int p0, int p1) throws BadLocationException
		{
			int stLine = findStartOfLine(p0, getDocument());
			int enLine = findEndOfLine(p1, getDocument());
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			try {
				g.setColor(Color.green);
				Document doc = getDocument();
				String s = doc.getText(stLine, enLine-stLine);
				Style[] styles = highlight(s, (p0-stLine), (p1-p0));
				String fname = handler.getPepaEditorFontFast().getName();
				int fsize = handler.getPepaEditorFontFast().getSize();
				for(int curr = 0; curr < styles.length; curr++) {
					Style c = styles[curr];
					g.setColor(c.c);
					g.setFont(new Font(fname, c.style, fsize));
					Segment segm = getLineBuffer();
					doc.getText(p0+curr, 1, segm);
					x = Utilities.drawTabbedText(segm, x, y, g, this, p0+curr);
				}
				g.setColor(Color.black);
				g.setFont(new Font(fname, Font.PLAIN, fsize));
			}
			catch(BadLocationException ex) {
				//System.out.println("ex = "+ex);
				//ex.printStackTrace();
			}
			return x;
		}
		
		private synchronized Style[] highlight(String s, int offset, int length)
		{
			Style[] styles = new Style[s.length()];
			for(int i = 0; i < styles.length; i++)
				styles[i] = PLAIN_S;
			
			match = pattern.matcher(s);
			
			
			int starter = 0;
			int end = 0;
			boolean contain = match.find();
			while(contain)
			{
				starter = match.start();
				end = match.end();
				
				for(int j = starter; j < end; j++)
				{
					styles[j] = handler.getPepaEditorCommentFast();
				}
				contain = match.find();
			}
			
			//System.out.println("styles.length = "+styles.length);
			//System.out.println("ret.length = "+length);
			//System.out.println("offset = "+offset);
			Style[]ret = new Style[length];
			for(int i = 0; i < ret.length; i++)
			{
				ret[i] = styles[i+offset];
			}
			
			return ret;
		}
		
		
		private synchronized int findStartOfLine(int p0, Document d)
		{
			int index = p0;
			String s = "";
			try
			{
				s = d.getText(index, 1);
			}
			catch(BadLocationException e)
			{
				return 0;
			}
			index--;
			if(!(!s.equals("\n") && index >= -1)) index--;//botch of the century, an alternative good code
			while(!s.equals("\n") && index >= -1)
			{
				try
				{
					s = d.getText(index, 1);
				}
				catch(BadLocationException e)
				{
					return 0;
				}
				index--;
			}
			index+=2;
			return index;
		}
		
		private synchronized int findEndOfLine(int p1, Document d)
		{
			int index = p1;
			String s = "";
			try
			{
				s = d.getText(index, 1);
			}
			catch(BadLocationException e)
			{
				return d.getLength();
			}
			index++;
			while(!s.equals("\n") && index <= d.getLength())
			{
				try
				{
					s = d.getText(index, 1);
				}
				catch(BadLocationException e)
				{
					return d.getLength()-1;
				}
				index++;
			}
			index--;
			return index;
		}
		
		
		
	}
	
}
