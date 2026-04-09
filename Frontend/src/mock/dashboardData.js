import { Bot, FileText, Users, FileUp, PlusCircle, MessageSquare, RefreshCw } from 'lucide-react';

export const mockDashboardData = {
  stats:[
    { title: 'Total Chatbots', value: '5', badge: '+2 new', badgeColor: 'bg-green-100 text-green-700', icon: Bot, iconColor: 'text-blue-600', iconBg: 'bg-blue-100' },
    { title: 'Total Documents', value: '120', badge: '+12 today', badgeColor: 'bg-green-100 text-green-700', icon: FileText, iconColor: 'text-purple-600', iconBg: 'bg-purple-100' },
    { title: 'Total Users', value: '8', badge: 'Active', badgeColor: 'bg-gray-100 text-gray-600', icon: Users, iconColor: 'text-orange-600', iconBg: 'bg-orange-100' },
  ],
  activities:[
    { id: 1, event: 'Uploaded document: policy.pdf', category: 'Knowledge Base', status: 'Success', time: '2 mins ago', icon: FileUp, iconBg: 'bg-blue-100', iconColor: 'text-blue-600' },
    { id: 2, event: 'Created chatbot: HR Bot', category: 'Chatbots', status: 'Completed', time: '1 hour ago', icon: PlusCircle, iconBg: 'bg-purple-100', iconColor: 'text-purple-600' },
    { id: 3, event: 'User asked: "Leave policy?"', category: 'Playground', status: 'Responded', time: '3 hours ago', icon: MessageSquare, iconBg: 'bg-orange-100', iconColor: 'text-orange-600' },
    { id: 4, event: 'Indexing vector database...', category: 'System', status: 'Processing', time: '5 hours ago', icon: RefreshCw, iconBg: 'bg-gray-100', iconColor: 'text-gray-600' },
  ]
};