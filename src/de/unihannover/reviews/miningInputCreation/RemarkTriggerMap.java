package de.unihannover.reviews.miningInputCreation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

public class RemarkTriggerMap {

    private static final String WHOLE_TICKET = "WT";

    public interface TicketInfoProvider {

        public abstract boolean containsChangesOutside(String commit, String file) throws IOException;

        public abstract boolean containsChangesInFileOutside(String commit, String file, int lineFrom, int lineTo) throws IOException;

    }

    private static abstract class AbstractMapNode {

        private static final AbstractMapNode[] EMPTY_ARRAY = new AbstractMapNode[0];

        private static int idCnt;

        private AbstractMapNode[] pointsTo = EMPTY_ARRAY;
        private final int id = idCnt++;

        public void addPointsTo(AbstractMapNode rn) {
            this.pointsTo = FlyweightArrayList.add(this.pointsTo, rn);
        }

        public void shrinkPointsTo() {
            this.pointsTo = FlyweightArrayList.shrink(this.pointsTo);
        }

        public boolean hasAtLeastOnePotentialTriggerNotIn(Collection<? extends AbstractMapNode> nodes) {
            return !nodes.containsAll(Arrays.asList(this.pointsTo));
        }

        public AbstractMapNode[] getPointsTo() {
            return this.pointsTo;
        }

        public final int getId() {
            return this.id;
        }

        public boolean hasAtLeastOnePotentialTriggerNotCoveredBy(
                TicketInfoProvider additionalInfos, String commit, String file, int lineFrom, int lineTo) throws IOException {

            for (final AbstractMapNode potentialTrigger : this.pointsTo) {
                if (potentialTrigger.isNotCompletelyCoveredBy(additionalInfos, commit, file, lineFrom, lineTo)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns true when the trigger contains changes that are not covered by the given ranges.
         */
        protected abstract boolean isNotCompletelyCoveredBy(
                TicketInfoProvider additionalInfos, String commit, String file, int lineFrom, int lineTo) throws IOException;

        public abstract void collectSelfAndChildren(OffsetBitset resultBuffer);

        public abstract void collectSelfAndChildrenIfMatchesAsString(
                        Set<Integer> idSet, String ownStringDescription, Set<String> resultBuffer);

        public abstract void shrinkSelfAndChildren();

		public abstract int countEntries();

		public abstract AbstractMapNode copyTriggerIfNotEmpty(Map<Integer, AbstractMapNode> targetMap);

    }

    private static abstract class NodeWithChildren<KC, C extends AbstractMapNode> extends AbstractMapNode {

//        public final C getChild(KC key) {
//            return this.children.get(key);
//        }

        public abstract Iterable<C> getChildren();

        public abstract void put(KC key, C child);

		public abstract Set<Entry<KC, C>> getChildEntries();

        @Override
        public void collectSelfAndChildren(OffsetBitset resultBuffer) {
            if (this.getPointsTo().length > 0) {
                resultBuffer.add(this.getId());
            }
            for (final C c : this.getChildren()) {
                c.collectSelfAndChildren(resultBuffer);
            }
        }

        @Override
        public void collectSelfAndChildrenIfMatchesAsString(
                        Set<Integer> idSet, String ownStringDescription, Set<String> resultBuffer) {
            if (idSet.contains(this.getId())) {
                resultBuffer.add(ownStringDescription);
            }
            for (final Entry<KC, C> e : this.getChildEntries()) {
                e.getValue().collectSelfAndChildrenIfMatchesAsString(
                                idSet, ownStringDescription + "," + e.getKey(), resultBuffer);
            }
        }

        @Override
        public void shrinkSelfAndChildren() {
            this.shrinkPointsTo();
            for (final C child : this.getChildren()) {
                child.shrinkSelfAndChildren();
            }
        }

		@Override
		public int countEntries() {
			//assumes that points to has been shrunk
			int ret = this.getPointsTo().length == 0 ? 0 : 1;
			for (final C child : this.getChildren()) {
				ret += child.countEntries();
			}
			return ret;
		}

		protected<N extends NodeWithChildren<KC, C>> N copyTriggerIfNotEmptyHelper(N ret, Map<Integer, AbstractMapNode> targetMap) {
			boolean empty = true;
			for (final AbstractMapNode pt : this.getPointsTo()) {
				final AbstractMapNode newTarget = targetMap.get(pt.id);
				if (newTarget != null) {
					ret.addPointsTo(newTarget);
					newTarget.addPointsTo(ret);
					empty = false;
				}
			}
			for (final Entry<KC, C> e : this.getChildEntries()) {
				final C childCopy = (C) e.getValue().copyTriggerIfNotEmpty(targetMap);
				if (childCopy != null) {
					ret.put(e.getKey(), childCopy);
					empty = false;
				}
			}
			return empty ? null : ret;
		}

    }

    private static final class TicketNode extends NodeWithChildren<String, FileInRevisionNode> {

        private final Map<String, FileInRevisionNode> children = new LinkedHashMap<>();

        public final FileInRevisionNode getChild(String key) {
            return this.children.get(key);
        }

        @Override
		public Iterable<FileInRevisionNode> getChildren() {
            return this.children.values();
        }

        @Override
		public final void put(String key, FileInRevisionNode child) {
            this.children.put(key, child);
        }

		@Override
		public Set<Entry<String, FileInRevisionNode>> getChildEntries() {
			return this.children.entrySet();
		}

        @Override
        protected boolean isNotCompletelyCoveredBy(
                TicketInfoProvider additionalInfos, String commit, String file, int lineFrom, int lineTo) throws IOException {
            if (additionalInfos.containsChangesOutside(commit, file)) {
                return true;
            }
            if ((lineFrom > 0 || lineTo < Integer.MAX_VALUE)
                && additionalInfos.containsChangesInFileOutside(commit, file, lineFrom, lineTo)) {
                return true;
            }
            return false;
        }

		@Override
		public TicketNode copyTriggerIfNotEmpty(Map<Integer, AbstractMapNode> targetMap) {
			return this.copyTriggerIfNotEmptyHelper(new TicketNode(), targetMap);
		}

    }

    private static final class FileInRevisionNode extends NodeWithChildren<Integer, LineNode> {

        private final TreeMap<Integer, LineNode> children = new TreeMap<>();

        public final LineNode getChild(Integer key) {
            return this.children.get(key);
        }

        public final Iterable<LineNode> getChildrenInRange(int startInclusive, int endExclusive) {
        	return this.children.subMap(startInclusive, endExclusive).values();
        }

        @Override
		public Iterable<LineNode> getChildren() {
            return this.children.values();
        }

        @Override
		public final void put(Integer key, LineNode child) {
            this.children.put(key, child);
        }

		@Override
		public Set<Entry<Integer, LineNode>> getChildEntries() {
			return this.children.entrySet();
		}

        @Override
        protected boolean isNotCompletelyCoveredBy(
                TicketInfoProvider additionalInfos, String commit, String file, int lineFrom, int lineTo) throws IOException {
            if ((lineFrom > 0 || lineTo < Integer.MAX_VALUE)
                && additionalInfos.containsChangesInFileOutside(commit, file, lineFrom, lineTo)) {
                return true;
            }
            return false;
        }

		@Override
		public FileInRevisionNode copyTriggerIfNotEmpty(Map<Integer, AbstractMapNode> targetMap) {
			return this.copyTriggerIfNotEmptyHelper(new FileInRevisionNode(), targetMap);
		}
    }

    private static final class LineNode extends AbstractMapNode {

        @Override
        protected boolean isNotCompletelyCoveredBy(
                TicketInfoProvider additionalInfos, String commit, String file, int lineFrom, int lineTo) {
            return false;
        }

        @Override
        public void collectSelfAndChildren(OffsetBitset resultBuffer) {
            resultBuffer.add(this.getId());
        }

        @Override
        public void collectSelfAndChildrenIfMatchesAsString(
                        Set<Integer> idSet, String ownStringDescription, Set<String> resultBuffer) {
            if (idSet.contains(this.getId())) {
                resultBuffer.add(ownStringDescription);
            }
        }

        @Override
        public void shrinkSelfAndChildren() {
            this.shrinkPointsTo();
        }

		@Override
		public int countEntries() {
			return 1;
		}

		@Override
		public LineNode copyTriggerIfNotEmpty(Map<Integer, AbstractMapNode> targetMap) {
			final LineNode ret = new LineNode();
			boolean empty = true;
			for (final AbstractMapNode pt : this.getPointsTo()) {
				final AbstractMapNode newTarget = targetMap.get(pt.id);
				if (newTarget != null) {
					ret.addPointsTo(newTarget);
					newTarget.addPointsTo(ret);
					empty = false;
				}
			}
			return empty ? null : ret;
		}
    }

    private final Map<String, TicketNode> remarkNodes = new HashMap<>();
    private final Map<String, TicketNode> triggerNodes = new HashMap<>();

    /**
     * Adds an entry to the map.
     * The syntax is "[REMARK];[TICKET];[TRIGGER]", and both REMARK and TRIGGER
     * can have one of three granularities: "WT" (whole ticket), "[commit],[file]" and "[commit],[file],[line]".
     */
    public void add(String mapFileLine) {
        final String[] parts = mapFileLine.split(";");
        if (parts.length != 3) {
            throw new AssertionError(Arrays.toString(parts) +  ";" + mapFileLine);
        }
        final AbstractMapNode rn = this.getOrCreate(this.remarkNodes, parts[1], parts[0]);
        final AbstractMapNode tn = this.getOrCreate(this.triggerNodes, parts[1], parts[2]);
        rn.addPointsTo(tn);
        tn.addPointsTo(rn);
    }

    private AbstractMapNode getOrCreate(Map<String, TicketNode> rootMap, String ticket, String id) {
        TicketNode t = rootMap.get(ticket);
        if (t == null) {
            t = new TicketNode();
            rootMap.put(ticket, t);
        }
        if (id.equals(WHOLE_TICKET)) {
            return t;
        }
        final String[] parts = id.split(",");
        assert parts.length <= 3;
        final String fileKey = this.createFileKey(parts[0], parts[1]);
        FileInRevisionNode f = t.getChild(fileKey);
        if (f == null) {
            f = new FileInRevisionNode();
            t.put(fileKey, f);
        }
        if (parts.length == 2) {
            return f;
        }
        final int line = Integer.parseInt(parts[2]);
        LineNode l = f.getChild(line);
        if (l == null) {
            l = new LineNode();
            f.put(line, l);
        }
        return l;
    }

    private String createFileKey(String commit, String file) {
        return commit + "," + file;
    }

    public TriggerClassification getClassification(
            TicketInfoProvider additionalInfos, String ticket, String commit, String file) throws IOException {
        return this.getClassification(additionalInfos, ticket, commit, file, 0, Integer.MAX_VALUE);
    }

    public TriggerClassification getClassification(
            TicketInfoProvider additionalInfos, String ticket, String commit, String file, int lineFrom, int lineTo) throws IOException {
        final Collection<AbstractMapNode> nodes = new LinkedHashSet<>();
		this.foreachTriggerNode(ticket, commit, file, lineFrom, lineTo, (AbstractMapNode node) -> nodes.add(node));
        boolean hadTriggers = false;
        for (final AbstractMapNode node : nodes) {
            for (final AbstractMapNode remark : node.getPointsTo()) {
                if (remark.hasAtLeastOnePotentialTriggerNotIn(nodes)
                    || remark.hasAtLeastOnePotentialTriggerNotCoveredBy(additionalInfos, commit, file, lineFrom, lineTo)) {
                    hadTriggers = true;
                } else {
                    return TriggerClassification.MUST_BE;
                }
            }
        }
        return hadTriggers ? TriggerClassification.CAN_BE : TriggerClassification.NO_TRIGGER;
    }

    public OffsetBitset getAllRemarksFor(String ticketId) {
        final TicketNode t = this.remarkNodes.get(ticketId);
        if (t == null) {
            return new OffsetBitset();
        }
        final OffsetBitset ret = new OffsetBitset();
        t.collectSelfAndChildren(ret);
        return ret;
    }

    public OffsetBitset getCoveredRemarks(String ticket, String commit, String file) {
        return this.getCoveredRemarks(ticket, commit, file, 0, Integer.MAX_VALUE);
    }

    public OffsetBitset getCoveredRemarks(String ticket, String commit, String file, int lineFrom, int lineTo) {
        final OffsetBitset ret = new OffsetBitset();
        this.foreachTriggerNode(ticket, commit, file, lineFrom, lineTo, (AbstractMapNode node) -> {
            for (final AbstractMapNode remark : node.getPointsTo()) {
                ret.add(remark.getId());
            }
        });
        return ret;
    }

    private void foreachTriggerNode(
            String ticket, String commit, String file, int lineFrom, int lineTo, Consumer<AbstractMapNode> consumer) {
        final TicketNode t = this.triggerNodes.get(ticket);
        if (t == null) {
            return;
        }
        consumer.accept(t);
        final FileInRevisionNode f = t.getChild(this.createFileKey(commit, file));
        if (f == null) {
            return;
        }
        consumer.accept(f);
        if (lineFrom <= 0 && lineTo == Integer.MAX_VALUE) {
            for (final LineNode l : f.getChildren()) {
            	consumer.accept(l);
            }
        } else {
            for (final LineNode l : f.getChildrenInRange(lineFrom, lineTo + 1)) {
            	consumer.accept(l);
            }
        }
    }

    public static RemarkTriggerMap loadFromFile(File file) throws IOException {
        final RemarkTriggerMap ret = new RemarkTriggerMap();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("NO_REMARKS;")) {
                    continue;
                }
                ret.add(line);
            }
        }
        ret.finishCreation();
        return ret;
    }

    public void finishCreation() {
        for (final AbstractMapNode n : this.triggerNodes.values()) {
            n.shrinkSelfAndChildren();
        }
        for (final AbstractMapNode n : this.remarkNodes.values()) {
            n.shrinkSelfAndChildren();
        }
    }

    public Integer getRemarkId(String ticket, String commit, String path) {
        final TicketNode t = this.remarkNodes.get(ticket);
        final FileInRevisionNode f = t.getChild(this.createFileKey(commit, path));
        return f.getId();
    }

    public Integer getRemarkId(String ticket, String commit, String path, int lineFrom) {
        final TicketNode t = this.remarkNodes.get(ticket);
        final FileInRevisionNode f = t.getChild(this.createFileKey(commit, path));
        return f.getChild(lineFrom).getId();
    }

    public Set<String> getRemarksWithIds(String ticketId, OffsetBitset remarkIds) {
        //don't care about performance here
        final Set<Integer> idSet = remarkIds.toSet();
        final TicketNode t = this.remarkNodes.get(ticketId);
        if (t == null) {
            return Collections.emptySet();
        }
        final Set<String> ret = new LinkedHashSet<>();
        t.collectSelfAndChildrenIfMatchesAsString(idSet, ticketId, ret);
        return ret;
    }

    public Set<String> getPotentialTriggersFor(String ticket, String commit, String path) {
        final TicketNode t = this.remarkNodes.get(ticket);
        final FileInRevisionNode f = t.getChild(this.createFileKey(commit, path));
        return this.getTriggersForNode(ticket, f);
    }

    public Set<String> getPotentialTriggersFor(String ticket, String commit, String path, int line) {
        final TicketNode t = this.remarkNodes.get(ticket);
        final FileInRevisionNode f = t.getChild(this.createFileKey(commit, path));
        return this.getTriggersForNode(ticket, f.getChild(line));
    }

    private Set<String> getTriggersForNode(String ticket, final AbstractMapNode node) {
        final OffsetBitset potentialTriggerIds = new OffsetBitset();
        for (final AbstractMapNode n : node.getPointsTo()) {
            potentialTriggerIds.add(n.id);
        }
        return this.getTriggersWithIds(ticket, potentialTriggerIds);
    }

    private Set<String> getTriggersWithIds(String ticketId, OffsetBitset triggerIds) {
        //don't care about performance here
        final Set<Integer> idSet = triggerIds.toSet();
        final TicketNode t = this.triggerNodes.get(ticketId);
        if (t == null) {
            return Collections.emptySet();
        }
        final Set<String> ret = new LinkedHashSet<>();
        t.collectSelfAndChildrenIfMatchesAsString(idSet, ticketId, ret);
        return ret;
    }

	public RemarkTriggerMap copyWithoutTicket(String ticket) {
		//when removing a whole ticket, a shallow copy is OK
		final RemarkTriggerMap ret = new RemarkTriggerMap();
		ret.remarkNodes.putAll(this.remarkNodes);
		ret.triggerNodes.putAll(this.triggerNodes);
		ret.remarkNodes.remove(ticket);
		ret.triggerNodes.remove(ticket);
		return ret;
	}

    public static interface RemarkFilter {

    	/**
    	 * Returns true iff the remark with the given data shall be left out of the map.
    	 * For whole file remarks, the line number is -1.
    	 */
		public abstract boolean isFiltered(String ticket, String commit, String file, int line);

    }

	public RemarkTriggerMap copyWithout(RemarkFilter filter) {
		final RemarkTriggerMap ret = new RemarkTriggerMap();
		final Map<Integer, AbstractMapNode> remarkMap = new HashMap<>();

		//copy all remark nodes that need to be copied
		for (final Entry<String, TicketNode> et : this.remarkNodes.entrySet()) {
			final TicketNode nt = new TicketNode();
			for (final Entry<String, FileInRevisionNode> ef : et.getValue().getChildEntries()) {
				final FileInRevisionNode nf = new FileInRevisionNode();
				final String[] commitAndFile = ef.getKey().split(",");
				for (final Entry<Integer, LineNode> el : ef.getValue().getChildEntries()) {
					final LineNode nl = new LineNode();
					if (!filter.isFiltered(et.getKey(), commitAndFile[0], commitAndFile[1], el.getKey())) {
						nf.put(el.getKey(), nl);
						remarkMap.put(el.getValue().getId(), nl);
					}
				}
				if (!nf.getChildEntries().isEmpty()
						|| (ef.getValue().getPointsTo().length > 0 && !filter.isFiltered(et.getKey(), commitAndFile[0], commitAndFile[1], -1))) {
					nt.put(ef.getKey(), nf);
					remarkMap.put(ef.getValue().getId(), nf);
				}
			}
			if (!nt.getChildEntries().isEmpty() || et.getValue().getPointsTo().length > 0) {
				ret.remarkNodes.put(et.getKey(), nt);
				remarkMap.put(et.getValue().getId(), nt);
			}
		}

		//copy the trigger nodes that still have remarks and establish the new pointsTo relation
		for (final Entry<String, TicketNode> et : this.triggerNodes.entrySet()) {
			final TicketNode copy = et.getValue().copyTriggerIfNotEmpty(remarkMap);
			if (copy != null) {
				ret.triggerNodes.put(et.getKey(), copy);
			}
		}

		ret.finishCreation();
		return ret;
	}

	public int countRemarks() {
		int ret = 0;
		for (final TicketNode n : this.remarkNodes.values()) {
			ret += n.countEntries();
		}
		return ret;
	}

}
