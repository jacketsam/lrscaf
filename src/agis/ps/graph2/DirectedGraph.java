/*
*File: agis.ps.graph2.DirectedGraph.java
*User: mqin
*Email: mqin@ymail.com
*Date: 2016年7月5日
*/
package agis.ps.graph2;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import agis.ps.link.Edge;
import agis.ps.seqs.Contig;
import agis.ps.util.MathTool;
import agis.ps.util.Parameter;
import agis.ps.util.Strand;

public class DirectedGraph extends Graph implements Serializable {

	private static final long serialVersionUID = 1L;
	private static Logger logger = LoggerFactory.getLogger(DirectedGraph.class);
	private static int TR_TIMES = 15; // for transitive reduction
	private Parameter paras = null;
	private Map<String, List<Contig>> adjTos = Collections.synchronizedMap(new HashMap<String, List<Contig>>());
	// for store multiple in and multiple out contig vertex;
	private List<Contig> mimos = new Vector<Contig>();
	private Directory directory = null;
	private IndexReader reader = null;
	private IndexSearcher searcher = null;
	private Analyzer analyzer = null;
	private QueryParser parser = null;
	

	public DirectedGraph(List<Edge> edges, Parameter paras) {
		super(edges);
		this.paras = paras; 
		initAdjTos();
		initCntIndexer();
	}

	private void initAdjTos() {
		if (adjTos == null)
			adjTos = Collections.synchronizedMap(new HashMap<String, List<Contig>>());
		if (adjTos != null)
			adjTos.clear();
		Collection<Map.Entry<String, Edge>> collections = this.edges.entrySet();
		Iterator<Map.Entry<String, Edge>> it = collections.iterator();
		while (it.hasNext()) {
			Map.Entry<String, Edge> entry = it.next();
			Edge e = entry.getValue();
			Contig origin = e.getOrigin();
			Contig terminus = e.getTerminus();
			String id = origin.getID();
			if (adjTos.containsKey(id)) {
				List<Contig> temp = adjTos.get(id);
				if (!temp.contains(terminus))
					temp.add(terminus);
				adjTos.replace(id, temp);
				if (temp.size() >= 3) {
					if (!mimos.contains(origin))
						mimos.add(origin);
				}
			} else {
				List<Contig> temp = new Vector<Contig>();
				temp.add(terminus);
				adjTos.put(id, temp);
			}

		}
	}

	public int getVertexAdjVerticesNum(Contig cnt) {
		int num = 0;
		num = getAdjVertices(cnt).size();
		return num;
	}
	
	@Override
	public boolean isDivergenceVertex(Contig cnt)
	{
		if(this.getVertexAdjVerticesNum(cnt) > 2)
			return true;
		else 
			return false;
	}

	// return next vertices by current and former vertex
	// but exclude former contig;
	@Override
	public List<Contig> getNextVertices(Contig current, Contig former) {
		List<Contig> adjs = this.adjTos.get(current.getID());
		List<Contig> values = new Vector<Contig>(adjs.size() - 1);
		Iterator<Contig> it = adjs.iterator();
		while (it.hasNext()) {
			Contig c = it.next();
			if (!c.equals(former))
				values.add(c);
		}
		if (values == null || values.isEmpty())
			return null;
		return values;
	}

	@Override
	public Contig getVertex(String id) {
		Contig cnt = null;
		if (this.vertices.containsKey(id))
			cnt = this.vertices.get(id);
		return cnt;
	}

	@Override
	public void transitiveReducting() {
		long start = System.currentTimeMillis();
		// it store the replication contig in the same transitive reduction,
		// it do need to do transitive reduction again
		if (mimos.size() == 0) {
			logger.info(this.getClass().getName() + " The graph do not contain transitive reduction structure!");
			return;
		}
		// the depth for searching, the alternative path could only accept 5
		// node;
		int depth = 5;
		for (Contig origin : mimos) {
			List<Contig> cnts = this.getAdjVertices(origin);
			Iterator<Contig> it = cnts.iterator();
			int indicator = cnts.size();
			try {
				while (it.hasNext()) {
					Contig c = it.next();
					LinkedList<Contig> path = new LinkedList<Contig>();
					path.addLast(origin);
					// path.addLast(c);
					this.transitiveReducting(c, origin, origin, depth, path);
					int temp = cnts.size();
					if (indicator != temp) {
						it = this.getAdjVertices(origin).iterator();
						indicator = temp;
					}
					// else
					// break;
				}
			} catch (Exception e) {
				logger.error(this.getClass().getName() + "\t" + e.getMessage());
			}
		}
		long end = System.currentTimeMillis();
		logger.info("Transitive Reducing, erase time: " + (end - start) + " ms");
		updateGraph();
	}

	// return contig is the next contig, if the return contig equal to start
	// point,
	// the recurrence break or if the depth is equal to defined depth return;
	private boolean transitiveReducting(Contig current, Contig former, Contig start, int depth,
			LinkedList<Contig> path) {
		path.addLast(current);
		List<Contig> cnts = this.getNextVertices(current, former);
		if (cnts == null) {
			path.removeLast();
			return false;
		}
		if (cnts.contains(start)) {
			int size = path.size();
			List<Edge> fEs = this.getEdgesInfo(start, path.get(1));
			List<Edge> aEs = this.getEdgesInfo(start, path.getLast());
			if (fEs == null)
				return false;
			if (aEs == null)
				return false;
			int fDist = fEs.get(0).getDistMean();
			int aDist = aEs.get(0).getDistMean();
			Contig end = null;
			LinkedList<Contig> alPath = new LinkedList<Contig>();
			if (fDist > aDist) {
				end = path.get(1);
				for (int i = size - 1; i >= 1; i--) {
					alPath.addLast(path.get(i));
				}
				alPath.addFirst(start);
			} else {
				end = path.getLast();
				alPath = path;
			}
			List<Edge> trEs = this.getEdgesInfo(start, end);
			List<Edge> alEs = new Vector<Edge>();
			int alDist = 0;
			int alSd = 0;
			int alSize = alPath.size();
			List<Integer> alDists = new Vector<Integer>(alSize * 2);
			List<Integer> alSds = new Vector<Integer>(alSize * 2);
			for (int i = 0; i < (alSize - 1); i++) {
				Contig now = alPath.get(i);
				Contig next = alPath.get(i + 1);
				List<Edge> temp = this.getEdgesInfo(now, next);
				alEs.addAll(temp);

				alDists.add(temp.get(0).getDistMean());
				alSds.add(temp.get(0).getDistSd());

				if (i > 0 && i < (alPath.size() - 1))
//					alDists.add(now.getLength());
					alDists.add(this.indexCntLength(now.getID()));
			}
			alDist = MathTool.sum(alDists);
			alSd = MathTool.avgSd(alSds);
			
			// check whether the direction is  conflict;
			Strand trStrand = null;
			for(Edge e : trEs)
			{
				if(e.getOrigin().equals(start) && e.getTerminus().equals(end))
				{
					trStrand = e.gettStrand();
					break;
				}
			}
			Strand alStrand = null;
			Contig preLst = alPath.get(alPath.size() - 2);
			List<Edge> lstEdges = this.getEdgesInfo(preLst, end);
			for(Edge e : lstEdges)
			{
				if(e.getOrigin().equals(preLst) && e.getTerminus().equals(end))
				{
					alStrand = e.gettStrand();
					break;
				}
			}
			if(!trStrand.equals(alStrand))
			{
				path.removeLast();
				return false;
			}

			int trDist = trEs.get(0).getDistMean();
			int trSd = trEs.get(0).getDistSd();
			int trSL = trEs.get(0).getLinkNum();

			int sd = trSd >= alSd ? trSd : alSd;
			int diff = trDist - alDist;
			int range = TR_TIMES * sd;

			if (diff >= -range && diff <= range) {
				// remove tr edges
				this.removeEdges(trEs);
				// modify alternative path edges;
				for (Edge e : alEs) {
					e.setLinkNum(e.getLinkNum() + trSL);
				}
				path.removeLast();
				return true;
			} else {
				path.removeLast();
				return false;
			}
		} else if (depth == 0) {
			path.removeLast();
			return false;
		} else {
			for (Contig c : cnts) {
				transitiveReducting(c, current, start, depth - 1, path);
			}
			path.removeLast();
			return false;
		}
	}

	@Override
	public void linearMergin() {
		// TODO Auto-generated method stub

	}

	@Override
	public void delErrorProneEdge(double ratio) {
		long start = System.currentTimeMillis();
		List<Edge> rmEdges = new Vector<Edge>(100);
		try {
			Iterator<Contig> it = mimos.iterator();
			while (it.hasNext()) {
				Contig c = it.next();
				String id = c.getID();
				List<Contig> adjs = adjTos.get(id);
				if (adjs == null)
					continue;
				int adjCount = adjs.size();
				Contig cnt = new Contig();
				cnt.setID(id);
				int[] sls = new int[adjCount];
				for (int i = 0; i < adjCount; i++) {
					Contig t = adjs.get(i);
					List<Edge> es = this.getEdgesInfo(cnt, t);
					sls[i] = es.get(0).getLinkNum();
				}
				Arrays.sort(sls);
				int max = sls[adjCount - 1];
				for (int i = 0; i < adjCount; i++) {
					Contig t = adjs.get(i);
					List<Edge> es = this.getEdgesInfo(cnt, t);
					int sl = es.get(0).getLinkNum();
					double r = (double) sl / max;
					if (r <= ratio) {
						rmEdges.addAll(es);
					}
				}
			}
		} catch (Exception e) {
			logger.debug(this.getClass().getName() + "\t" + e.getMessage());
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}
		logger.info(this.getClass().getName() + "\tDelete error prone edges:" + rmEdges.size());
		this.removeEdges(rmEdges);
		this.updateGraph();
		long end = System.currentTimeMillis();
		logger.info("Error prone Edge deleting, erase time: " + (end - start) + " ms");
	}

	// return the specified contig's adjacent contigs, including adjacent
	// from other contigs or adjacent to other contigs;
	@Override
	public List<Contig> getAdjVertices(Contig cnt) {
		List<Contig> values = this.adjTos.get(cnt.getID());
		return values;
	}

	// return the next vertex by the specified current and former contig;
	// excluding return divergence vertex;
	@Override
	public Contig getNextVertex(Contig current, Contig former) {
		Contig next = null;
		List<Contig> adjs = this.getAdjVertices(current);
		if (former == null) {
			if (adjs.size() == 1) {
				// normal case, only one adjacent vertex
				next = adjs.get(0);
			} else if (adjs.size() == 2) {
				// normal case, two adjacent vertex; two temporary contigs
				// variables, return the more supported links vertex
				Contig tCnt1 = adjs.get(0);
				Contig tCnt2 = adjs.get(1);
				List<Edge> tEdg1 = getEdgesInfo(current, tCnt1);
				List<Edge> tEdg2 = getEdgesInfo(current, tCnt2);
				// according to the supported link number to decide which contig
				// will be the next;
				int tEdgSL1 = tEdg1.get(0).getLinkNum();
				int tEdgSL2 = tEdg2.get(0).getLinkNum();

				if (tEdgSL1 > tEdgSL2)
					next = tCnt1;
				else
					next = tCnt2;
				tEdg1 = null;
				tEdg2 = null;
			} else {
				// abnormal case, larger than two vertices return null;
				next = null;
			}
		} else // former not null
		{
			if (adjs.size() == 1) {
				next = adjs.get(0);
				// checking whether the former vertex equal to next
				if (!next.equals(former))
					throw new IllegalArgumentException(
							"DirectedGraph: The former vertex was not equal to next vertex when "
									+ "the adjacent vertex of the current was only one!");
			} else if (adjs.size() == 2) {
				// normal case, return the not selected vertex
				for (Contig c : adjs) {
					if (!c.equals(former))
						next = c;
				}
			} else {
				// abnormal case, return the former
				// next = former;
				next = null;
			}
		}
		return next;
	}

	// return the next vertex by the specified current and former contig;
	// including return divergence vertex; and former could not be null for
	// triad requirement
	public Contig getNextVertex2(Contig current, Contig former) {
		Contig next = null;
		List<Contig> adjs = this.getAdjVertices(current);
		if (former == null) {
			throw new IllegalArgumentException(
					this.getClass().getName() + "\t" + "The former vertex could not be null!");
		}

		if (adjs.size() == 1) {
			next = adjs.get(0);
			// checking whether the former vertex equal to next
			if (!next.equals(former))
				throw new IllegalArgumentException("DirectedGraph: The former vertex was not equal to next vertex when "
						+ "the adjacent vertex of the current was only one!");
		} else if (adjs.size() == 2) {
			// normal case, return the not selected vertex
			for (Contig c : adjs) {
				if (!c.equals(former))
					next = c;
			}
		} else {
			// abnormal case, return the former
			// next = former;
			next = null;
		}
		return next;
	}

	@Override
	public List<Edge> getEdgesInfo(Contig start, Contig end) {
		List<Edge> info = new Vector<Edge>(2);
		String id1 = start.getID() + "->" + end.getID();
		String id2 = end.getID() + "->" + start.getID();
		if (this.edges.containsKey(id1))
			info.add(this.edges.get(id1));
		if (this.edges.containsKey(id2))
			info.add(this.edges.get(id2));
		if (info.size() == 0)
			return null;
		return info;
	}

	@Override
	public boolean removeEdge(Edge e) {
		boolean isRemove = false;
		Contig origin = e.getOrigin();
		Contig terminus = e.getTerminus();
		String id = origin.getID() + "->" + terminus.getID();
		if (this.edges.containsKey(id)) {
			if (this.edges.remove(id) != null) {
				List<Contig> temp = this.adjTos.get(origin.getID());
				if (temp.contains(terminus))
					temp.remove(terminus);
				this.adjTos.replace(origin.getID(), temp);
				isRemove = true;
			}
		}
		return isRemove;
	}

	@Override
	public void removeEdges(List<Edge> edges) {
		Iterator<Edge> it = edges.iterator();
		while (it.hasNext()) {
			Edge e = it.next();
			Contig origin = e.getOrigin();
			Contig terminus = e.getTerminus();
			String id = origin.getID() + "->" + terminus.getID();
			if (this.edges.containsKey(id))
				this.edges.remove(id);
			List<Contig> temp = this.adjTos.get(origin.getID());
			if (temp.contains(terminus))
				temp.remove(terminus);
			this.adjTos.replace(origin.getID(), temp);
		}
		// updateGraph();
	}

	public void updateGraph() {
		initAdjTos();
	}
	
	private void initCntIndexer()
	{
		try {
			String path = paras.getOutFolder() + System.getProperty("file.separator") + "cnt.index";
			directory = new SimpleFSDirectory(new File(path).toPath());
			reader = DirectoryReader.open(directory);
			searcher = new IndexSearcher(reader);
			analyzer = new StandardAnalyzer();
			parser = new QueryParser("id", analyzer);
		} catch (IOException e) {
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}
	}
	
	private int indexCntLength(String id)
	{
		int len = 0;
		try {
			Query query = parser.parse(id);
			TopDocs tds = searcher.search(query, 10);
			for (ScoreDoc sd : tds.scoreDocs) {
				Document doc = searcher.doc(sd.doc);
				len = Integer.valueOf(doc.get("len"));
			}
		} catch (Exception e) {
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}
		return len;
	}
	
	@Override
	public void delTips()
	{
		long start = System.currentTimeMillis();
		List<Edge> rmEdges = new Vector<Edge>(100);
		try {
			Iterator<Contig> it = mimos.iterator();
			while (it.hasNext()) {
				Contig c = it.next();
				String id = c.getID();
				List<Contig> adjs = adjTos.get(id);
				if (adjs == null)
					continue;
				int adjCount = adjs.size();
				// only considering divergence point
				if(adjCount > 2)
				{
					for(int i = 0; i < adjCount; i++)
					{
						Contig next = adjs.get(i);
						List<Contig> nextAdjs = adjTos.get(next.getID());
						if(nextAdjs == null)
							continue;
						if(nextAdjs.size() != 1)
							continue;
						// if the next adjacent is not equal to former divergence point;
						if(!nextAdjs.get(0).equals(c))
							continue;
						List<Edge> es = this.getEdgesInfo(c, next);
						rmEdges.addAll(es);
					}
				}
			}
		} catch (Exception e) {
			logger.error(this.getClass().getName() + "\t" + e.getMessage());
		}
		logger.info(this.getClass().getName() + "\tDelete tip edges:" + rmEdges.size());
		this.removeEdges(rmEdges);
		this.updateGraph();
		long end = System.currentTimeMillis();
		logger.info("Tip edge deleting, erase time: " + (end - start) + " ms");
	}
}
