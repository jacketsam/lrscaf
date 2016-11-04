/*
*File: agis.ps.link2.M5s.java
*User: mqin
*Email: mqin@ymail.com
*Date: 2016年9月23日
*/
package agis.ps.link2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import agis.ps.link.M5Record;
import agis.ps.util.Parameter;
import agis.ps.util.Strand;

/**
 * Encapsulate m5 file into a class;
 * all the data store in array separately;
 * @author mqin
 *
 */

public class M5FileEncapsulate {
	
	private static Logger logger = LoggerFactory.getLogger(M5FileEncapsulate.class);
	private int [] qIds;
	private int [] qLengths;
	private int [] qStart;
	private int [] qEnd;
	private int [] tIds;
	private int [] tLengths;
	private int [] tStart;
	private int [] tEnd;
	private byte [] tStrand;
	//private short [] score;
	//private int [] mapQV;
	private int [] matchs;
	private int [] misMatchs;
	private int [] inserts;
	private int [] deletes;
	private int index = 0;
	private int pIdIndex = 0;
	private int cIdIndex = 0;
	private Map<String, Integer> pIdHashForward = new HashMap<String, Integer>();
//	private Map<Integer, String> pIdHashReverse = new HashMap<Integer, String>();
	private Map<String, Integer> cIdHashForward = new LinkedHashMap<String, Integer>();
	private Map<String, Integer> cntLens = new HashMap<String, Integer>();
//	private Map<Integer, String> cIdHashReverse = new HashMap<Integer, String>();
	private Parameter paras;
	
	public M5FileEncapsulate()
	{
		
	}
	
	public M5FileEncapsulate(int size, Parameter paras)
	{
		this.qIds = new int[size];
		this.qLengths = new int[size];
		this.qStart = new int[size];
		this.qEnd = new int[size];
		this.tIds = new int[size];
		this.tLengths = new int[size];
		this.tStart = new int[size];
		this.tEnd = new int[size];
		this.tStrand = new byte[size];
		this.matchs = new int[size];
		this.misMatchs = new int[size];
		this.inserts = new int[size];
		this.deletes = new int[size];
		this.paras = paras;
	}
	
	public M5Record getM5Record(int index)
	{
		if(index < 0 || index >= qIds.length)
			return null;
		M5Record m5 = new M5Record();
		m5.setqName(String.valueOf(qIds[index]));
		m5.setqLength(qLengths[index]);
		m5.setqStart(qStart[index]);
		m5.setqEnd(qEnd[index]);
		m5.settName(String.valueOf(tIds[index]));
		m5.settLength(tLengths[index]);
		m5.settStart(tStart[index]);
		m5.settEnd(tEnd[index]);
		m5.settStrand(tStrand[index] == 0 ? Strand.FORWARD : Strand.REVERSE);
		m5.setNumMatch(matchs[index]);
		m5.setNumMismatch(misMatchs[index]);
		m5.setNumIns(inserts[index]);
		m5.setNumDel(deletes[index]);
		return m5;
	}
	
	// using init array size
	public void addRecord(M5Record m5)
	{
		this.addRecord(m5, index);
		index++;
	}
	
	private void addRecord(M5Record m5, int index)
	{
		// for the last element to insert into array
		String pId = m5.getqName();
		int pIdInt = 0;
		String cId = m5.gettName();
		int cIdInt = 0;
		if(pIdHashForward.containsKey(pId))
		{
			pIdInt = pIdHashForward.get(pId);
		} else
		{
			pIdInt = pIdIndex;
			pIdHashForward.put(pId, pIdIndex);
			pIdIndex++;
		}
		if(cIdHashForward.containsKey(cId))
		{
			cIdInt = cIdHashForward.get(cId);
		} else
		{
			cIdInt = cIdIndex;
			cIdHashForward.put(cId, cIdIndex);
			cIdIndex++;
		}
		qIds[index] = pIdInt;
		qLengths[index] = m5.getqLength();
		qStart[index] = m5.getqStart();
		qEnd[index] = m5.getqEnd();
		tIds[index] = cIdInt;
		tLengths[index] = m5.gettLength();
		tStart[index] = m5.gettStart();
		tEnd[index] = m5.gettEnd();
		if(m5.gettStrand().equals(Strand.FORWARD))
			tStrand[index] = 0;
		else
			tStrand[index] = 1;
		matchs[index] = m5.getNumMatch();
		misMatchs[index] = m5.getNumMismatch();
		inserts[index] = m5.getNumIns();
		deletes[index] = m5.getNumDel();
		if(!cntLens.containsKey(cId))
			cntLens.put(cId, m5.gettLength());
	}
	
	/**
	 * build contig file encapsulate from the aligned record.
	 * There will be only id and length, omitted seq data;
	 * And adding sequence data in ScaffoldWriter class later.
	 * write this recode contig id's map relationship into file;
	 * @return
	 */
	public CntFileEncapsulate getCntFileEncapsulate()
	{
		CntFileEncapsulate cntFile = new CntFileEncapsulate();
		Iterator<String> it = cIdHashForward.keySet().iterator();
		File file = null;
		FileWriter fw = null;
		BufferedWriter bw = null;
		try
		{
			file = new File(paras.getOutFolder() + System.getProperty("file.separator") + "CntIdsMap.info");
			fw = new FileWriter(file);
			bw = new BufferedWriter(fw);
			while(it.hasNext())
			{
				String oid = it.next();
				Integer nid = this.cIdHashForward.get(oid);
				int len = cntLens.get(oid);
				cntFile.addLength(nid, oid, len);
				bw.write(nid + " => " + oid);
				bw.newLine();
			}
			bw.flush();
			bw.close();
		}catch(IOException e)
		{
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}catch(Exception e)
		{
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}finally
		{
			try{
				if(bw != null)
					bw.close();
			} catch(Exception e)
			{
				logger.error(this.getClass().getName() + "\t" + e.getMessage());
			}
		}
		return cntFile;
	}
}


