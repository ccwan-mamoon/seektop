package io.github.seektop.ui.mail

import androidx.compose.runtime.Composable
import io.github.seektop.ui.resources.Res
import io.github.seektop.ui.resources.mail_admin_field_actions
import io.github.seektop.ui.resources.mail_admin_field_address
import io.github.seektop.ui.resources.mail_admin_field_aiauditedat
import io.github.seektop.ui.resources.mail_admin_field_aiauditresult
import io.github.seektop.ui.resources.mail_admin_field_aiauditstatus
import io.github.seektop.ui.resources.mail_admin_field_aiscore
import io.github.seektop.ui.resources.mail_admin_field_alias
import io.github.seektop.ui.resources.mail_admin_field_archivecount
import io.github.seektop.ui.resources.mail_admin_field_archivelimit
import io.github.seektop.ui.resources.mail_admin_field_attemptcount
import io.github.seektop.ui.resources.mail_admin_field_authinfo
import io.github.seektop.ui.resources.mail_admin_field_authscore
import io.github.seektop.ui.resources.mail_admin_field_bcc
import io.github.seektop.ui.resources.mail_admin_field_bodyhtml
import io.github.seektop.ui.resources.mail_admin_field_bodytext
import io.github.seektop.ui.resources.mail_admin_field_cancelwindowuntil
import io.github.seektop.ui.resources.mail_admin_field_cc
import io.github.seektop.ui.resources.mail_admin_field_clientaccessadmin
import io.github.seektop.ui.resources.mail_admin_field_clientaccessuser
import io.github.seektop.ui.resources.mail_admin_field_clientip
import io.github.seektop.ui.resources.mail_admin_field_color
import io.github.seektop.ui.resources.mail_admin_field_conditions
import io.github.seektop.ui.resources.mail_admin_field_contenthtml
import io.github.seektop.ui.resources.mail_admin_field_contentid
import io.github.seektop.ui.resources.mail_admin_field_contenttext
import io.github.seektop.ui.resources.mail_admin_field_contenttype
import io.github.seektop.ui.resources.mail_admin_field_createdat
import io.github.seektop.ui.resources.mail_admin_field_details
import io.github.seektop.ui.resources.mail_admin_field_dkim
import io.github.seektop.ui.resources.mail_admin_field_dmarc
import io.github.seektop.ui.resources.mail_admin_field_domain
import io.github.seektop.ui.resources.mail_admin_field_domainid
import io.github.seektop.ui.resources.mail_admin_field_donotdisturb
import io.github.seektop.ui.resources.mail_admin_field_effectivequota
import io.github.seektop.ui.resources.mail_admin_field_email
import io.github.seektop.ui.resources.mail_admin_field_error
import io.github.seektop.ui.resources.mail_admin_field_errormessage
import io.github.seektop.ui.resources.mail_admin_field_filename
import io.github.seektop.ui.resources.mail_admin_field_folderid
import io.github.seektop.ui.resources.mail_admin_field_forwarddeleteat
import io.github.seektop.ui.resources.mail_admin_field_forwarddeletedays
import io.github.seektop.ui.resources.mail_admin_field_forwardkeepcopy
import io.github.seektop.ui.resources.mail_admin_field_forwardto
import io.github.seektop.ui.resources.mail_admin_field_fromaddress
import io.github.seektop.ui.resources.mail_admin_field_fromemail
import io.github.seektop.ui.resources.mail_admin_field_fromname
import io.github.seektop.ui.resources.mail_admin_field_hasattachments
import io.github.seektop.ui.resources.mail_admin_field_icon
import io.github.seektop.ui.resources.mail_admin_field_id
import io.github.seektop.ui.resources.mail_admin_field_importance
import io.github.seektop.ui.resources.mail_admin_field_index
import io.github.seektop.ui.resources.mail_admin_field_inreplyto
import io.github.seektop.ui.resources.mail_admin_field_isactive
import io.github.seektop.ui.resources.mail_admin_field_isdefault
import io.github.seektop.ui.resources.mail_admin_field_isflagged
import io.github.seektop.ui.resources.mail_admin_field_isinline
import io.github.seektop.ui.resources.mail_admin_field_ispinned
import io.github.seektop.ui.resources.mail_admin_field_isread
import io.github.seektop.ui.resources.mail_admin_field_isreserved
import io.github.seektop.ui.resources.mail_admin_field_issystem
import io.github.seektop.ui.resources.mail_admin_field_istrustedsender
import io.github.seektop.ui.resources.mail_admin_field_isverifiedsender
import io.github.seektop.ui.resources.mail_admin_field_lastcontactat
import io.github.seektop.ui.resources.mail_admin_field_lastfailedat
import io.github.seektop.ui.resources.mail_admin_field_limit
import io.github.seektop.ui.resources.mail_admin_field_listid
import io.github.seektop.ui.resources.mail_admin_field_listunsubscribe
import io.github.seektop.ui.resources.mail_admin_field_listunsubscribepost
import io.github.seektop.ui.resources.mail_admin_field_mailboxaddress
import io.github.seektop.ui.resources.mail_admin_field_mailboxcount
import io.github.seektop.ui.resources.mail_admin_field_mailboxid
import io.github.seektop.ui.resources.mail_admin_field_mailcount
import io.github.seektop.ui.resources.mail_admin_field_mailid
import io.github.seektop.ui.resources.mail_admin_field_messageid
import io.github.seektop.ui.resources.mail_admin_field_name
import io.github.seektop.ui.resources.mail_admin_field_nextretryat
import io.github.seektop.ui.resources.mail_admin_field_offset
import io.github.seektop.ui.resources.mail_admin_field_orgid
import io.github.seektop.ui.resources.mail_admin_field_originalownerid
import io.github.seektop.ui.resources.mail_admin_field_ownerid
import io.github.seektop.ui.resources.mail_admin_field_owneruserid
import io.github.seektop.ui.resources.mail_admin_field_pendingat
import io.github.seektop.ui.resources.mail_admin_field_precedence
import io.github.seektop.ui.resources.mail_admin_field_previousstatus
import io.github.seektop.ui.resources.mail_admin_field_priority
import io.github.seektop.ui.resources.mail_admin_field_quotasource
import io.github.seektop.ui.resources.mail_admin_field_receivedat
import io.github.seektop.ui.resources.mail_admin_field_releaseafter
import io.github.seektop.ui.resources.mail_admin_field_remindat
import io.github.seektop.ui.resources.mail_admin_field_requestedat
import io.github.seektop.ui.resources.mail_admin_field_retrycount
import io.github.seektop.ui.resources.mail_admin_field_rulescore
import io.github.seektop.ui.resources.mail_admin_field_scheduledat
import io.github.seektop.ui.resources.mail_admin_field_scoredetails
import io.github.seektop.ui.resources.mail_admin_field_sendattempts
import io.github.seektop.ui.resources.mail_admin_field_sendchannelid
import io.github.seektop.ui.resources.mail_admin_field_sendchannelname
import io.github.seektop.ui.resources.mail_admin_field_senddisabled
import io.github.seektop.ui.resources.mail_admin_field_senderemail
import io.github.seektop.ui.resources.mail_admin_field_sendername
import io.github.seektop.ui.resources.mail_admin_field_sendoutboundip
import io.github.seektop.ui.resources.mail_admin_field_sentat
import io.github.seektop.ui.resources.mail_admin_field_size
import io.github.seektop.ui.resources.mail_admin_field_snoozeduntil
import io.github.seektop.ui.resources.mail_admin_field_sortorder
import io.github.seektop.ui.resources.mail_admin_field_source
import io.github.seektop.ui.resources.mail_admin_field_spamreason
import io.github.seektop.ui.resources.mail_admin_field_spamscore
import io.github.seektop.ui.resources.mail_admin_field_spf
import io.github.seektop.ui.resources.mail_admin_field_status
import io.github.seektop.ui.resources.mail_admin_field_storagequota
import io.github.seektop.ui.resources.mail_admin_field_storageused
import io.github.seektop.ui.resources.mail_admin_field_subject
import io.github.seektop.ui.resources.mail_admin_field_threadid
import io.github.seektop.ui.resources.mail_admin_field_toaddress
import io.github.seektop.ui.resources.mail_admin_field_total
import io.github.seektop.ui.resources.mail_admin_field_unreadcount
import io.github.seektop.ui.resources.mail_admin_field_updatedat
import io.github.seektop.ui.resources.mail_admin_field_usagepercent
import io.github.seektop.ui.resources.mail_admin_field_userid
import io.github.seektop.ui.resources.mail_admin_field_username
import io.github.seektop.ui.resources.mail_admin_field_verificationcode
import io.github.seektop.ui.resources.mail_admin_section_check
import io.github.seektop.ui.resources.mail_admin_section_contacts
import io.github.seektop.ui.resources.mail_admin_section_filters
import io.github.seektop.ui.resources.mail_admin_section_folders
import io.github.seektop.ui.resources.mail_admin_section_mailbox_release_holds
import io.github.seektop.ui.resources.mail_admin_section_mailbox_settings
import io.github.seektop.ui.resources.mail_admin_section_mailboxes
import io.github.seektop.ui.resources.mail_admin_section_mails_counts
import io.github.seektop.ui.resources.mail_admin_section_mails_unread
import io.github.seektop.ui.resources.mail_admin_section_quotas
import io.github.seektop.ui.resources.mail_admin_section_signatures
import io.github.seektop.ui.resources.mail_admin_section_storage
import io.github.seektop.ui.resources.mail_admin_section_tags
import io.github.seektop.ui.resources.mail_admin_upload
import org.jetbrains.compose.resources.stringResource

@Composable
fun mailAdminLabel(name: String): String = when (name) {
    "mailboxes" -> stringResource(Res.string.mail_admin_section_mailboxes)
    "contacts" -> stringResource(Res.string.mail_admin_section_contacts)
    "folders" -> stringResource(Res.string.mail_admin_section_folders)
    "tags" -> stringResource(Res.string.mail_admin_section_tags)
    "filters" -> stringResource(Res.string.mail_admin_section_filters)
    "signatures" -> stringResource(Res.string.mail_admin_section_signatures)
    "mailbox-release-holds" -> stringResource(Res.string.mail_admin_section_mailbox_release_holds)
    "mails/unread" -> stringResource(Res.string.mail_admin_section_mails_unread)
    "mails/counts" -> stringResource(Res.string.mail_admin_section_mails_counts)
    "storage" -> stringResource(Res.string.mail_admin_section_storage)
    "quotas" -> stringResource(Res.string.mail_admin_section_quotas)
    "mailbox-settings" -> stringResource(Res.string.mail_admin_section_mailbox_settings)
    "check" -> stringResource(Res.string.mail_admin_section_check)
    "address" -> stringResource(Res.string.mail_admin_field_address)
    "email" -> stringResource(Res.string.mail_admin_field_email)
    "name" -> stringResource(Res.string.mail_admin_field_name)
    "color" -> stringResource(Res.string.mail_admin_field_color)
    "icon" -> stringResource(Res.string.mail_admin_field_icon)
    "conditions" -> stringResource(Res.string.mail_admin_field_conditions)
    "actions" -> stringResource(Res.string.mail_admin_field_actions)
    "priority" -> stringResource(Res.string.mail_admin_field_priority)
    "isActive" -> stringResource(Res.string.mail_admin_field_isactive)
    "contentText" -> stringResource(Res.string.mail_admin_field_contenttext)
    "contentHtml" -> stringResource(Res.string.mail_admin_field_contenthtml)
    "mailboxId" -> stringResource(Res.string.mail_admin_field_mailboxid)
    "isDefault" -> stringResource(Res.string.mail_admin_field_isdefault)
    "error" -> stringResource(Res.string.mail_admin_field_error)
    "total" -> stringResource(Res.string.mail_admin_field_total)
    "limit" -> stringResource(Res.string.mail_admin_field_limit)
    "offset" -> stringResource(Res.string.mail_admin_field_offset)
    "id" -> stringResource(Res.string.mail_admin_field_id)
    "alias" -> stringResource(Res.string.mail_admin_field_alias)
    "domainId" -> stringResource(Res.string.mail_admin_field_domainid)
    "ownerId" -> stringResource(Res.string.mail_admin_field_ownerid)
    "isReserved" -> stringResource(Res.string.mail_admin_field_isreserved)
    "doNotDisturb" -> stringResource(Res.string.mail_admin_field_donotdisturb)
    "archiveCount" -> stringResource(Res.string.mail_admin_field_archivecount)
    "archiveLimit" -> stringResource(Res.string.mail_admin_field_archivelimit)
    "clientAccessAdmin" -> stringResource(Res.string.mail_admin_field_clientaccessadmin)
    "clientAccessUser" -> stringResource(Res.string.mail_admin_field_clientaccessuser)
    "sendDisabled" -> stringResource(Res.string.mail_admin_field_senddisabled)
    "forwardTo" -> stringResource(Res.string.mail_admin_field_forwardto)
    "forwardKeepCopy" -> stringResource(Res.string.mail_admin_field_forwardkeepcopy)
    "forwardDeleteDays" -> stringResource(Res.string.mail_admin_field_forwarddeletedays)
    "status" -> stringResource(Res.string.mail_admin_field_status)
    "createdAt" -> stringResource(Res.string.mail_admin_field_createdat)
    "ownerUserId" -> stringResource(Res.string.mail_admin_field_owneruserid)
    "orgId" -> stringResource(Res.string.mail_admin_field_orgid)
    "mailboxAddress" -> stringResource(Res.string.mail_admin_field_mailboxaddress)
    "previousStatus" -> stringResource(Res.string.mail_admin_field_previousstatus)
    "requestedAt" -> stringResource(Res.string.mail_admin_field_requestedat)
    "releaseAfter" -> stringResource(Res.string.mail_admin_field_releaseafter)
    "attemptCount" -> stringResource(Res.string.mail_admin_field_attemptcount)
    "lastFailedAt" -> stringResource(Res.string.mail_admin_field_lastfailedat)
    "originalOwnerId" -> stringResource(Res.string.mail_admin_field_originalownerid)
    "folderId" -> stringResource(Res.string.mail_admin_field_folderid)
    "fromAddress" -> stringResource(Res.string.mail_admin_field_fromaddress)
    "fromName" -> stringResource(Res.string.mail_admin_field_fromname)
    "toAddress" -> stringResource(Res.string.mail_admin_field_toaddress)
    "subject" -> stringResource(Res.string.mail_admin_field_subject)
    "bodyText" -> stringResource(Res.string.mail_admin_field_bodytext)
    "bodyHtml" -> stringResource(Res.string.mail_admin_field_bodyhtml)
    "messageId" -> stringResource(Res.string.mail_admin_field_messageid)
    "inReplyTo" -> stringResource(Res.string.mail_admin_field_inreplyto)
    "threadId" -> stringResource(Res.string.mail_admin_field_threadid)
    "isRead" -> stringResource(Res.string.mail_admin_field_isread)
    "isFlagged" -> stringResource(Res.string.mail_admin_field_isflagged)
    "hasAttachments" -> stringResource(Res.string.mail_admin_field_hasattachments)
    "isPinned" -> stringResource(Res.string.mail_admin_field_ispinned)
    "importance" -> stringResource(Res.string.mail_admin_field_importance)
    "spamReason" -> stringResource(Res.string.mail_admin_field_spamreason)
    "pendingAt" -> stringResource(Res.string.mail_admin_field_pendingat)
    "snoozedUntil" -> stringResource(Res.string.mail_admin_field_snoozeduntil)
    "remindAt" -> stringResource(Res.string.mail_admin_field_remindat)
    "forwardDeleteAt" -> stringResource(Res.string.mail_admin_field_forwarddeleteat)
    "receivedAt" -> stringResource(Res.string.mail_admin_field_receivedat)
    "size" -> stringResource(Res.string.mail_admin_field_size)
    "spamScore" -> stringResource(Res.string.mail_admin_field_spamscore)
    "authScore" -> stringResource(Res.string.mail_admin_field_authscore)
    "ruleScore" -> stringResource(Res.string.mail_admin_field_rulescore)
    "aiScore" -> stringResource(Res.string.mail_admin_field_aiscore)
    "aiAuditStatus" -> stringResource(Res.string.mail_admin_field_aiauditstatus)
    "aiAuditResult" -> stringResource(Res.string.mail_admin_field_aiauditresult)
    "aiAuditedAt" -> stringResource(Res.string.mail_admin_field_aiauditedat)
    "scoreDetails" -> stringResource(Res.string.mail_admin_field_scoredetails)
    "listUnsubscribe" -> stringResource(Res.string.mail_admin_field_listunsubscribe)
    "listUnsubscribePost" -> stringResource(Res.string.mail_admin_field_listunsubscribepost)
    "listId" -> stringResource(Res.string.mail_admin_field_listid)
    "precedence" -> stringResource(Res.string.mail_admin_field_precedence)
    "authInfo" -> stringResource(Res.string.mail_admin_field_authinfo)
    "verificationCode" -> stringResource(Res.string.mail_admin_field_verificationcode)
    "fromEmail" -> stringResource(Res.string.mail_admin_field_fromemail)
    "senderName" -> stringResource(Res.string.mail_admin_field_sendername)
    "senderEmail" -> stringResource(Res.string.mail_admin_field_senderemail)
    "spf" -> stringResource(Res.string.mail_admin_field_spf)
    "dkim" -> stringResource(Res.string.mail_admin_field_dkim)
    "dmarc" -> stringResource(Res.string.mail_admin_field_dmarc)
    "isVerifiedSender" -> stringResource(Res.string.mail_admin_field_isverifiedsender)
    "isTrustedSender" -> stringResource(Res.string.mail_admin_field_istrustedsender)
    "domain" -> stringResource(Res.string.mail_admin_field_domain)
    "details" -> stringResource(Res.string.mail_admin_field_details)
    "cc" -> stringResource(Res.string.mail_admin_field_cc)
    "bcc" -> stringResource(Res.string.mail_admin_field_bcc)
    "source" -> stringResource(Res.string.mail_admin_field_source)
    "clientIp" -> stringResource(Res.string.mail_admin_field_clientip)
    "errorMessage" -> stringResource(Res.string.mail_admin_field_errormessage)
    "sendChannelId" -> stringResource(Res.string.mail_admin_field_sendchannelid)
    "sendChannelName" -> stringResource(Res.string.mail_admin_field_sendchannelname)
    "sendOutboundIp" -> stringResource(Res.string.mail_admin_field_sendoutboundip)
    "sendAttempts" -> stringResource(Res.string.mail_admin_field_sendattempts)
    "retryCount" -> stringResource(Res.string.mail_admin_field_retrycount)
    "nextRetryAt" -> stringResource(Res.string.mail_admin_field_nextretryat)
    "cancelWindowUntil" -> stringResource(Res.string.mail_admin_field_cancelwindowuntil)
    "sentAt" -> stringResource(Res.string.mail_admin_field_sentat)
    "scheduledAt" -> stringResource(Res.string.mail_admin_field_scheduledat)
    "userId" -> stringResource(Res.string.mail_admin_field_userid)
    "sortOrder" -> stringResource(Res.string.mail_admin_field_sortorder)
    "mailCount" -> stringResource(Res.string.mail_admin_field_mailcount)
    "unreadCount" -> stringResource(Res.string.mail_admin_field_unreadcount)
    "isSystem" -> stringResource(Res.string.mail_admin_field_issystem)
    "lastContactAt" -> stringResource(Res.string.mail_admin_field_lastcontactat)
    "updatedAt" -> stringResource(Res.string.mail_admin_field_updatedat)
    "mailId" -> stringResource(Res.string.mail_admin_field_mailid)
    "filename" -> stringResource(Res.string.mail_admin_field_filename)
    "contentType" -> stringResource(Res.string.mail_admin_field_contenttype)
    "isInline" -> stringResource(Res.string.mail_admin_field_isinline)
    "contentId" -> stringResource(Res.string.mail_admin_field_contentid)
    "index" -> stringResource(Res.string.mail_admin_field_index)
    "username" -> stringResource(Res.string.mail_admin_field_username)
    "mailboxCount" -> stringResource(Res.string.mail_admin_field_mailboxcount)
    "storageUsed" -> stringResource(Res.string.mail_admin_field_storageused)
    "storageQuota" -> stringResource(Res.string.mail_admin_field_storagequota)
    "effectiveQuota" -> stringResource(Res.string.mail_admin_field_effectivequota)
    "usagePercent" -> stringResource(Res.string.mail_admin_field_usagepercent)
    "quotaSource" -> stringResource(Res.string.mail_admin_field_quotasource)
    "upload" -> stringResource(Res.string.mail_admin_upload)
    else -> name
}
