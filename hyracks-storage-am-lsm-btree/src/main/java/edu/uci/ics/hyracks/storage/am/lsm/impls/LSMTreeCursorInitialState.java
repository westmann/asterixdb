package edu.uci.ics.hyracks.storage.am.lsm.impls;

import edu.uci.ics.hyracks.storage.am.common.api.ICursorInitialState;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;

public class LSMTreeCursorInitialState implements ICursorInitialState {

	private int numBTrees;
	private ITreeIndexFrameFactory leafFrameFactory;
	private MultiComparator cmp;
	private LSMTree lsm;
	
	public LSMTreeCursorInitialState(int numBTrees, ITreeIndexFrameFactory leafFrameFactory, MultiComparator cmp, LSMTree lsm) {
		this.numBTrees = numBTrees;
		this.leafFrameFactory = leafFrameFactory;
		this.cmp = cmp;
		this.lsm = lsm;
	}
	
	public int getNumBTrees() {
		return numBTrees;
	}

	public ITreeIndexFrameFactory getLeafFrameFactory() {
		return leafFrameFactory;
	}

	public MultiComparator getCmp() {
		return cmp;
	}

	@Override
	public ICachedPage getPage() {
		return null;
	}

	@Override
	public void setPage(ICachedPage page) {
	}

	public LSMTree getLsm() {
		return lsm;
	}

}
