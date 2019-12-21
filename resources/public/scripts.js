function updateCurRule() {
  $.get('curRule.html', function(rule) {afterRuleLoad(rule)});
}

function afterRuleLoad(rule) {
  window.ruleTool.curRule.clearGutter("sizeGutter");
  window.ruleTool.curRule.setValue(rule);
  updateRuleStyles();
  updateStatusAndNav();
  updateSizeGutter();
}

function updateStatusAndNav() {
  $('#statusAndNav').load('statusAndNav.html');
}

function updateRuleStyles() {
  $.post('ruleStyling.json', getRuleObject())
    .done(function(styling) {applyStyling(styling)})
    .fail(function(jqXHR, textStatus, errorThrown) {alert('error ' + textStatus + ',' + errorThrown)});
}

function applyStyling(styling) {
  let cm = window.ruleTool.curRule;
  let allOldMarks = cm.getAllMarks();
  for (i = 0; i < allOldMarks.length; i++) {
    allOldMarks[i].clear();
  }
  
  for (i = 0; i < styling.length; i++) {
    cm.markText(styling[i].from, styling[i].to, {className: styling[i].className});
  }
}

function performInit() {
  window.ruleTool = {};
  window.ruleTool.curRule = CodeMirror($('#curRuleHolder')[0], {
    gutters: ["sizeGutter"]
  });
  window.ruleTool.sizeCache = {};
  updateCurRule();
  window.setInterval(function() {updateStatusAndNav()}, 5000)
}

function goToRule(target, step) {
  $.post('goToRule.html', {'target': target, 'step': step}, function(rule) {afterRuleLoad(rule)});
}

function getRuleObject() {
  let rule = window.ruleTool.curRule.getValue();
  return {'rule': rule};
}

function evaluateRule() {
  $.post('evaluateRule.html', getRuleObject(), function(newRule) {afterRuleLoad(newRule)})
    .fail(function(jqXHR, textStatus, errorThrown) {alert('error ' + textStatus + ',' + errorThrown)});
}

function startAgent() {
  $.post("startAgent.html", function(data) {alert(data)} )
}

function stopAgent() {
  $.post("stopAgent.html", function(data) {alert(data)} )
}

function saveResults() {
  $.post("saveResults.html", function(data) {alert(data)} )
}

function purgeRules() {
  let countToKeep = prompt("This will remove ALL but a certain number of the results! How many results shall be kept (approx.)?", "100");
  if (countToKeep == null) {
    return;
  }
  $.post("purgeRules.html", {"countToKeep": countToKeep}, function(data) {alert(data)} )
}

function submitPostViaHiddenForm(url, params) {
    var f = $("<form target='_blank' method='POST' style='display:none;'></form>").attr({
        action: url
    }).appendTo(document.body);

    for (var i in params) {
        if (params.hasOwnProperty(i)) {
            $('<input type="hidden" />').attr({
                name: i,
                value: params[i]
            }).appendTo(f);
        }
    }

    f.submit();
    f.remove();
}

function statisticsForWholeDataset() {
  submitPostViaHiddenForm('statisticsForWholeDataset.html', {})
}

function getSelectionObject() {
  let cm = window.ruleTool.curRule;
  let value = cm.getValue();
  return {
    'selection': cm.getSelection(),
    'beforeSelection': value.substring(0, cm.indexFromPos(cm.getCursor("from")))}
}

function getSelectionAndRuleObject() {
  let cm = window.ruleTool.curRule;
  let value = cm.getValue();
  return {
    'selection': cm.getSelection(),
    'beforeSelection': value.substring(0, cm.indexFromPos(cm.getCursor("from"))),
    'rule': value}
}

function statisticsForCurrentSelection() {
  submitPostViaHiddenForm('statisticsForSelection.html', getSelectionObject())
}

function statisticsForInverseSelection() {
  submitPostViaHiddenForm('statisticsForInverseSelection.html', getSelectionObject())
}

function acceptSelection() {
  $.post("acceptSelection.html", getSelectionObject(), function() {updateRuleStyles()})  
}

function keepSelectionAsCandidate() {
  $.post("keepSelectionAsCandidate.html", getSelectionObject(), function() {updateRuleStyles()})  
}

function rejectSelection() {
  $.post("rejectSelection.html", getSelectionObject(), function() {updateRuleStyles()})  
}

function rejectPattern() {
  let so = getSelectionObject();
  let pattern = prompt("Please enter the pattern to reject", so.selection);
  if (pattern == null) {
    return;
  }
  so.selection = pattern;
  $.post("rejectPattern.html", so, function() {updateRuleStyles()})  
}

function showRestrictions() {
  submitPostViaHiddenForm('showRestrictions.html', {})
}

function analyzeDataPointDetails() {
  submitPostViaHiddenForm('analyzeDataPointDetails.html', {})
}

function analyzeBadChoices() {
  submitPostViaHiddenForm('analyzeBadChoices.html', getRuleObject())
}

function fillSizeCacheAndUpdateGutter() {
  $.post('ruleSizes.json', getRuleObject())
    .done(function(sizes) {
      cacheSizes(sizes);
      updateSizeGutter();
    })
    .fail(function(jqXHR, textStatus, errorThrown) {alert('error ' + textStatus + ',' + errorThrown)});
}

function cacheSizes(sizes) {
  let sc = window.ruleTool.sizeCache;
  for (let i = 0; i < sizes.length; i++) {
    sc[sizes[i].text] = sizes[i].values;
  }
}

function updateSizeGutter() {
  let sc = window.ruleTool.sizeCache;
  
  let sizes = [];
  let missingRule = false;
  window.ruleTool.curRule.eachLine(function(lineHandle) {
    let text = lineHandle.text.trim();
    if (isRuleLine(text)) {
      if (isDefined(sc[text])) {
        sizes.push(sc[text].recordCount);
      } else {
        missingRule = true;
      }
    }
  });
  
  if (missingRule) {
    fillSizeCacheAndUpdateGutter();
    return;
  }
  
  sizes.sort();
  let lowerThird = sizes[Math.floor(sizes.length / 3)];
  let upperThird = sizes[Math.floor(sizes.length * 2 / 3)];
  
  window.ruleTool.curRule.eachLine(function(lineHandle) {
    let text = lineHandle.text.trim();
    if (isRuleLine(text)) {
      let dta = sc[text];
      let recCnt = dta.recordCount;
      let type;
      if (recCnt <= 15) {
        type = "XS";
      } else if (recCnt < lowerThird) {
        type = "S";
      } else if (recCnt > upperThird) {
        type = "L";
      } else {
        type = "M";
      }
      window.ruleTool.curRule.setGutterMarker(
          lineHandle, "sizeGutter", makeMarker(type, 
              "records: " + recCnt));
    }
  });
}

function isRuleLine(text) {
  return text.startsWith('(') || text.startsWith('or (');
}

function isDefined(v) {
  return typeof(v) != "undefined";
}

function makeMarker(type, msg) {
	var marker = document.createElement("div");
	marker.title = msg;
	marker.style.color = "#dd0000";
	marker.innerHTML = type;
	return marker;
}

function removeRecord() {
  let recordId = prompt("Please enter record ID");
  if (recordId == null) {
    return;
  }
  $.post("removeRecord.html", {'record': recordId}, function(data) {alert(data)})  
}

function addCalculatedColumn() {
  let name = prompt("Please enter the name of the new numeric column");
  if (name == null) {
    return;
  }
  let calculationScript = prompt("Please enter JavaScript code to calculate the value for " + name);
  if (calculationScript == null) {
    return;
  }
  $.post("addCalculatedColumn.html", {'name': name, 'calculationScript': calculationScript}, function(data) {alert(data)})  
}

function highlightRow(rowElement) {
  let row = $(rowElement);
  let table = row.closest("table");
  let wasHighlighted = row.hasClass("highlightedRow");
  
  let rows = table.get(0).rows;
  for (let i = 0; i < rows.length; i++) {
    let cur = $(rows[i]);
    if (cur.is(row) && !wasHighlighted) {
      cur.addClass("highlightedRow");
    } else {
      cur.removeClass("highlightedRow");
    }
  }  
}

function setLimit(target, curValue) {
  let value = prompt("Please enter upper limit", curValue);
  if (value == null) {
    return;
  }
  $.post("setLimit.html", {'target': target, 'value': value}, function(data) {if (data.length > 0) alert(data)});  
}

function undoRestriction(restr, classification, isPattern) {
  $.post("undoRestriction.html", {'selection': restr, 'classification': classification, 'isPattern': isPattern},
    function(data) {if (data.length > 0) alert(data)});  
}

function saveData() {
  $.post("saveData.html", function(data) {alert(data)} )
}

