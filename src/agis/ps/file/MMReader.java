/*
*File: agis.ps.file.MMReader.java
*User: mqin
*Email: mqin@ymail.com
*Date: 2017年2月22日
*/
package agis.ps.file;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import agis.ps.link.MRecord;
import agis.ps.util.Parameter;
import agis.ps.util.Strand;

public class MMReader extends AlignmentFileReader {
	
	private static final Logger logger = LoggerFactory.getLogger(MMReader.class);

	public MMReader(Parameter paras) {
		super(paras);
	}
	
	@Override
	protected MRecord initMRecord(String [] arrs)
	{
		MRecord record = new MRecord();
		record.setqName(arrs[0]);
		record.setqLength(arrs[1]);
		record.setqStart(arrs[2]);
		record.setqEnd(arrs[3]);
		record.settName(arrs[5]);
		record.settLength(arrs[6]);
		int match = Integer.valueOf(arrs[9]);
		int allMatch = Integer.valueOf(arrs[10]);
//		double identity = Double.valueOf(((double)match) /allMatch);
//		identity = Double.valueOf(String.format("%.4f", identity));
//		record.setIdentity(identity);
		// for internation decimal format
		double identity = Math.round((double)match / allMatch * 10000) / 10000.0;
		record.setIdentity(identity);
		if(arrs[4].equals("+"))
			record.settStrand(Strand.FORWARD);
		else
			record.settStrand(Strand.REVERSE);
		record.settStart(arrs[7]);
		record.settEnd(arrs[8]);
		int cms = 0;
		for(int i = 11; i < arrs.length; i++)
		{
			if(arrs[i].startsWith("cm:i:"))
			{
				cms = Integer.valueOf(arrs[i].split("cm:i:")[1]);
				break;
			}
		}
		/*
		if(arrs[12].startsWith("cm:i:"))
			cms = Integer.valueOf(arrs[12].split("cm:i:")[1]);
		else
			cms = Integer.valueOf(arrs[13].split("cm:i:")[1]);
		*/
		if(cms <= paras.getMmcm())
			return null;
		logger.debug(record.toString());
		return record;
	}
}


