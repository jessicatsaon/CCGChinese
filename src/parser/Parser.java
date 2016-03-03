/***************************  LICENSE  *******************************
* This file is part of UBL.
* 
* UBL is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as 
* published by the Free Software Foundation, either version 3 of the 
* License, or (at your option) any later version.
* 
* UBL is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public 
* License along with UBL.  If not, see <http://www.gnu.org/licenses/>.
***********************************************************************/



package parser;

import java.util.*;
import java.io.*;

import learn.*;
import lambda.*;

import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.BaseAnalysis;
import org.ansj.splitWord.analysis.IndexAnalysis;
import org.ansj.splitWord.analysis.NlpAnalysis;
import org.ansj.splitWord.analysis.ToAnalysis;

public class Parser {

	public Parser(){
		binaryRules = new LinkedList();
		binaryRules.add(new CompRule());
		lexicon = new Lexicon();
		if (empty) {
		    binaryRules.add(new EmptyCatRule());
		}
	}
    public Parser(Lexicon lex){
    	this();
    	lexicon=lex;
        }

    public void makeFeatures(){
    	parseFeatures = new LinkedList<ParseFeatureSet>();
    	int fStartIndex =0;

    	firstLexFeatIndex = fStartIndex;
    	System.out.println("Start LexPhi: "+fStartIndex);
    	
    	
    	lexicalFeatures = new LinkedList<LexicalFeatureSet>();
    	lexPhi = new LexiconFeatSet(lexicon.getLexicon(),fStartIndex,theta); 
    	lexicalFeatures.add(lexPhi);
    	
    	
    	parseFeatures.add(new ExpParseFeats());
    }


    /**
     * tokenize the sentence
     * @param input
     * @return
     */
	public static List tokenize(String input){
		// first tokenize the string
		List tokens = new LinkedList();
		
		//parse with whitespace (e.g. english)
		/*StringTokenizer st = new StringTokenizer(input);
		while (st.hasMoreTokens()){
			tokens.add(st.nextToken());
		}*/
		
		//parse with character (e.g. chinese)
		/*for (int ii=0; ii<input.length(); ii++) {
			tokens.add(input.charAt(ii));
			//System.out.println("add token: " + input.charAt(ii));
		}*/
		
		//Chinese. parse after segmentation
		/**
		 * four segmentation methods
		 * BaseAnalysis.parse(input);
		 * ToAnalysis.parse(input)
		 * NlpAnalysis.parse(input)
		 * IndexAnalysis.parse(input)
		 * return type List<Term>
		 */
		List<Term> myTokens= NlpAnalysis.parse(input);
		String tmp;
		for (Term tt : myTokens) {
			tmp = tt.toString();
			int tmpIndex = tmp.indexOf("/");
			tmp = tmp.substring(0, tmpIndex);
			if (tmp.equalsIgnoreCase("，")) continue;
			tokens.add(tmp);
		}

		System.out.println(tokens);
		
		
		
		return tokens;
	}

	/**
	 * Return a parsed chart of the input, pruning each cell to 
	 * pruneN entries.
	 *
	 * Uses the CKY algorithm.
	 **/

	public Chart parse(String input, Exp pruningSem, boolean computeInside){

		System.out.println("parsing: " + input + " : " + pruningSem);
		List tokens = tokenize(input);
		//System.out.println("tokens size = " + tokens.size());
		/*for (String ii : tokens) {
			System.out.print(ii);
		}
		System.out.println();*/
		
		Globals.tokens=tokens;
		Globals.lastWordIndex=tokens.size()-1;

		// create a chart and add the input words
		Chart chart = new Chart(tokens);
		
		if (pruningSem!=null){
			// What's this?
            chart.setPruningSem(pruningSem);
		}

		// initialize cells list with the lexical rule
		lexicon.addCells(chart);
		if (tempLex!=null){
			tempLex.addCells(chart);
		}

		int size = tokens.size();
		List temp = new LinkedList();
		// now do the CKY parsing:
		for (int len = 1; len<size; len++){
			for (int begin = 0; begin<size-len; begin++){
				for (int split=0; split<len; split++){
					temp.clear();
					Iterator leftIter 
					= chart.getCellsIterator(begin,begin+split);
					while (leftIter.hasNext()){
						Cell left = (Cell)leftIter.next();
						Iterator rightIter 
						= chart.getCellsIterator(begin+split+1,begin+len);
						while (rightIter.hasNext()){
							Cell right = (Cell)rightIter.next();
							Iterator rules = binaryRules.iterator();
							while (rules.hasNext()){
								((BinaryParseRule)rules.next())
								.newCellsFrom(left,right,temp);
							}
						}
					}
					chart.addAll(temp);
				}
			}
		}

		chart.pruneParseResults(pruneN);

		// store the parse results
		allParses = chart.getParseResults();
		allParses = removeRepeats(allParses);

		// find best parses for test and for generating
		// new lexical items.
		bestParses = findBestParses(allParses);
		return chart;
	}

	public void setGlobals(){
		Globals.theta=theta;
		Globals.lexPhi=lexPhi;
		Globals.parseFeatures=parseFeatures;
		Globals.lexicalFeatures=lexicalFeatures;
		Globals.lexRule=lexicon;
	}


	public void parseTimed(String input, Exp pruningSem, String mes){
		setGlobals();
		long start = System.currentTimeMillis();
		if (mes!=null){
			System.out.println(mes);
			System.out.flush();
		}
		chart = parse(input,pruningSem,true);
		long end = System.currentTimeMillis();
		double time = (end-start)/1000.0;
		if (mes!=null)
			System.out.println(" parse time: "+time+" sec.");
	}


	public Chart parse(String input){
		return parse(input,null,true);
	}
	
	public Chart parse(String input,Exp pruningSem){
		return parse(input,pruningSem,true);
	}
	
	public Lexicon returnLex(){
		return lexicon;
	}

	public void setLexicon(Lexicon l){
		lexicon=l;
	}

	public void setTempLexicon(Lexicon l){
		tempLex=l;
	}


	public Exp bestSem(){
		if (bestParses.size()!=1){
			return null;
		}
		Exp e = bestParses.get(0).getExp();
		return e;
	}

	public List<ParseResult> bestParses(){
		return bestParses;
	}

	public boolean hasParseFor(Exp sem){
		for (ParseResult p : allParses){
			if (p.getExp().equals(sem))
				return true;
		}
		return false;
	}

	private List<ParseResult> findBestParses(List<ParseResult> all, Exp e){
		List<ParseResult> best = new LinkedList<ParseResult>();
		double bestScore = -Double.MAX_VALUE;
		for (ParseResult p : all){
			if (p.getExp().inferType()!=null){
				if ((e==null || p.getExp().equals(e))){
					if (p.getScore()==bestScore)
						best.add(p);
					if (p.getScore()>bestScore){
						bestScore = p.getScore();
						best.clear();
						best.add(p);
					}
				}
			}
		}
		return best;
	}

	private List<ParseResult> findBestParses(List<ParseResult> all){
		return findBestParses(all,null);
	}

	private List<ParseResult> removeRepeats(List<ParseResult> all){
		List<ParseResult> bestList = new LinkedList<ParseResult>();
		for (int i=0; i<all.size(); i++){
			ParseResult e_i = all.get(i);
			boolean best = true;
			for (int j=i+1; j<all.size(); j++){
				ParseResult e_j = all.get(j);
				if (e_i.getExp().equals(e_j.getExp()) && 
						e_i.getScore() <= e_j.getScore()){
					best = false;
					break;
				}
			}
			if (best) bestList.add(e_i);
		}
		return bestList;
	}

	/**
	 *  Returns the features for the highest-score current parse(s).
	 */
	public HashVector getFeats() {
		HashVector result = new HashVector();
		//result.reset(theta.size(),0.0);
		for (ParseResult p : bestParses)
			p.getFeats(result);
		if (bestParses.size()>1)
			result.divideBy(bestParses.size());
		return result;
	}

	/**
	 *  Returns the features for the highest-score current parse
	 *  with semantics that equal sem.
	 */
	public HashVector getFeats(Exp sem) {
		HashVector result = new HashVector();
		List<ParseResult> pr = findBestParses(allParses,sem);
		for (ParseResult p : pr){
			p.getFeats(result);
		}
		if (pr.size()>1)
			result.divideBy(pr.size());
		return result;
	}

	/**
	 *  Adds the input vector into the current parameters.
	 */
	public void updateParams(HashVector p){
		p.addTimesInto(1,theta);
	}

	public HashVector getParams(){
		return theta;
	}

	public void setParams(HashVector p){
		theta = new HashVector(p);
	}

	/**
	 * Expands the lexicon and updates the weight vector 
	 * and feature indices.  Does not add entries that are
	 * already present.
	 */
	public void addLexEntries(List lex){
		for (int i=0; i<lex.size(); i++){
			addLexEntry((LexEntry)lex.get(i));
		}
	}

	public void addLexEntry(LexEntry l){
		//assumes that the LexicalFeatures are at the 
		// end of the feature/weight vector
		if (!lexicon.contains(l)){
			lexicon.addLexEntry(l);
			int start = firstLexFeatIndex;
			for (LexicalFeatureSet lfs : lexicalFeatures){
				lfs.setStartIndex(start);
				lfs.addLexEntry(l,theta);
				start+=lfs.numFeats();
			}
		}	
	}

	/**
	 * Finds the lexical items used to produce the highest
	 * scoring parse with semantics sem.
	 */
	public List getMaxLexEntriesFor(Exp sem){
		List result = new LinkedList();
		for (ParseResult p : findBestParses(allParses,sem)){
			result.addAll(p.getLexEntries());
		}
		return result;
	}

	/**
	 * Finds the lexical items used to produce the highest
	 * scoring parse.
	 */
	public List getMaxLexEntries(){
		List result = new LinkedList();
		for (ParseResult p : bestParses){
			result.addAll(p.getLexEntries());
		}
		return result;
	}

	public boolean hasExp(Exp sem){
		for (ParseResult p : allParses){
			if (p.getExp().equals(sem)){
				return true;
			}
		}
		return false;
	}

	public ParseResult bestParse(Exp sem){
		List<ParseResult> result = findBestParses(allParses,sem);
		if (result.size()>0) return result.get(0);
		return null;
	}

	public List<ParseResult> getAllParses(){
		return allParses;
	}

	public Chart getChart(){
		return chart;
	}

	public void printLexicon(){
		System.out.println("--- Lexicon:");
		System.out.println(lexicon);
		System.out.println("------------");
	}
	public void printLexiconWithWeights(){
		lexicon.printLexiconWithWeights();
	}

	// the current parses
	List<ParseResult> allParses;
	List<ParseResult> bestParses;

	List binaryRules;  
	List unaryRules;
	Lexicon lexicon;  
	Lexicon tempLex;

	// global parameters for how to parse
	public static boolean expSpanFeats = false;
	public static boolean empty=true;
	
	// the maximum number of cells allowed in each span
	public static int pruneN=50;

	// the context
	public static boolean parseContext = false;

	// the chart that stores the last parse
	Chart chart;

	// the current set of weights
	HashVector theta = new HashVector();

	// features
	public LexiconFeatSet lexPhi = null;
	public List<ParseFeatureSet> parseFeatures = new LinkedList<ParseFeatureSet>();
	public List<LexicalFeatureSet> lexicalFeatures = new LinkedList<LexicalFeatureSet>();
	int firstLexFeatIndex=0;

	public static void main(String[] args){
		Parser p = new Parser();
		p.tokenize("我想订个有二十人包间的餐厅，人均80元，地坛附近，大众家常菜");
	}
}
