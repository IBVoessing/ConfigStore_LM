package com.voessing.vcde.tooladapter.models;

import java.util.List;

public class TrelloTask {

	public static class Card {
		private String name;
		private String desc;
		private String due;
		private String start;
		private List<String> idMembers;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public String getDesc() {
			return desc;
		}
		public void setDesc(String desc) {
			this.desc = desc;
		}
		public String getDue() {
			return due;
		}
		public void setDue(String due) {
			this.due = due;
		}
		public String getStart() {
			return start;
		}
		public void setStart(String start) {
			this.start = start;
		}
		public List<String> getIdMembers() {
			return idMembers;
		}
		public void setIdMembers(List<String> idMembers) {
			this.idMembers = idMembers;
		}
		public List<Checklist> getChecklists() {
			return checklists;
		}
		public void setChecklists(List<Checklist> checklists) {
			this.checklists = checklists;
		}
		private List<Checklist> checklists;
		
		public static class Checklist {
			private String name;
			private List<CheckItem> checkItems;
			
			public String getName() {
				return name;
			}
			public void setName(String name) {
				this.name = name;
			}
			public List<CheckItem> getCheckItems() {
				return checkItems;
			}
			public void setCheckItems(List<CheckItem> checkItems) {
				this.checkItems = checkItems;
			}

		}

		public static class CheckItem {
			private String name;
			private String due;
			private String idMember;
			
			public String getName() {
				return name;
			}
			public void setName(String name) {
				this.name = name;
			}
			public String getDue() {
				return due;
			}
			public void setDue(String due) {
				this.due = due;
			}
			public String getIdMember() {
				return idMember;
			}
			public void setIdMember(String idMember) {
				this.idMember = idMember;
			}

		}
	}

}
